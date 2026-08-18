/*
 * Copyright 2013-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.awspring.cloud.dynamodb.repository.query;

import io.awspring.cloud.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.dynamodb.core.EntityQueryResult;
import io.awspring.cloud.dynamodb.repository.Query;
import io.awspring.cloud.dynamodb.request.DynamoDbPageRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbQueryRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class StringBasedAggregateQuery implements RepositoryQuery {

	private final DynamoDbQueryMethod queryMethod;
	private final DynamoDbOperations operations;
	private final Query query;
	private final ValueExpressionDelegate valueExpressionDelegate;
	private final Class<?> aggregateClass;

	public StringBasedAggregateQuery(DynamoDbQueryMethod queryMethod, DynamoDbOperations operations,
			ValueExpressionDelegate valueExpressionDelegate) {
		Assert.isTrue(queryMethod.hasAnnotatedQuery(), "Query method must have an @Query annotation: " + queryMethod);

		this.queryMethod = queryMethod;
		this.operations = operations;
		this.query = queryMethod.getQueryAnnotation();
		this.valueExpressionDelegate = valueExpressionDelegate;
		this.aggregateClass = queryMethod.getResultProcessor().getReturnedType().getDomainType();
	}

	@Override
	public DynamoDbQueryMethod getQueryMethod() {
		return this.queryMethod;
	}

	@Override
	@Nullable
	@SuppressWarnings("unchecked")
	public Object execute(Object[] parameters) {
		DynamoDbParametersParameterAccessor accessor = new DynamoDbParametersParameterAccessor(queryMethod, parameters);

		if (!StringUtils.hasText(query.keyConditionExpression())) {
			throw new InvalidDataAccessApiUsageException(
					"@Query on an AggregateRepository method requires keyConditionExpression() to be set. "
							+ "Scan-based aggregate reads are not supported (the fold requires a PK equality). "
							+ "Method: " + queryMethod);
		}

		var key = bind(query.keyConditionExpression(), accessor);

		Map<String, String> names = expressionNames();
		Map<String, Object> values = new LinkedHashMap<>(key.values());

		String filterExpression = null;
		if (StringUtils.hasText(query.filterExpression())) {
			var filter = bind(query.filterExpression(), accessor);
			filterExpression = filter.expression();
			values.putAll(filter.values());
		}

		applyExpressionValues(values, accessor);

		DynamoDbQueryRequest.Builder builder = DynamoDbQueryRequest.request()
				.withKeyConditionExpression(key.expression())
				.withExpressionAttributeNames(names.isEmpty() ? null : names)
				.withExpressionAttributeValues(values.isEmpty() ? null : values)
				.withConsistentRead(query.consistentRead());

		if (StringUtils.hasText(query.indexName())) {
			builder.withIndexName(query.indexName());
		}

		if (StringUtils.hasText(filterExpression)) {
			builder.withFilterExpression(filterExpression);
		}

		DynamoDbQueryRequest request = builder.build();
		EntityQueryResult<Object> result = (EntityQueryResult<Object>) operations.queryAggregate(aggregateClass,
				request, DynamoDbPageRequest.of(null));

		return unwrap(result);
	}

	@Nullable
	private Object unwrap(@Nullable EntityQueryResult<Object> result) {
		boolean optional = Optional.class.isAssignableFrom(queryMethod.getDeclaredReturnType());

		if (result == null) {
			return optional ? Optional.empty() : null;
		}
		Integer count = result.getCount();
		if (count == null || count == 0) {
			return optional ? Optional.empty() : null;
		}
		Object entity = result.getEntity();
		return optional ? Optional.ofNullable(entity) : entity;
	}

	private QueryExpressions.Bound bind(String rawExpression, ParameterAccessor accessor) {
		return QueryExpressions.bind(valueExpressionDelegate, queryMethod.getParameters(), rawExpression, accessor);
	}

	private Map<String, String> expressionNames() {
		return QueryExpressions.expressionNames(query);
	}

	private void applyExpressionValues(Map<String, Object> values, ParameterAccessor accessor) {
		QueryExpressions.applyExpressionValues(query, valueExpressionDelegate, queryMethod.getParameters(), accessor,
				values);
	}
}
