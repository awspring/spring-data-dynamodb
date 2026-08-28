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
package io.awspring.spring.data.dynamodb.repository.query;

import io.awspring.spring.data.dynamodb.core.DynamoDbOperations;
import io.awspring.spring.data.dynamodb.core.EntityQueryResult;
import io.awspring.spring.data.dynamodb.repository.Query;
import io.awspring.spring.data.dynamodb.request.DynamoDbPageRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbQueryRequest;
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
public class StringBasedItemCollectionQuery implements RepositoryQuery {

	private final DynamoDbQueryMethod queryMethod;
	private final DynamoDbOperations operations;
	private final @Nullable Query query;
	private final ValueExpressionDelegate valueExpressionDelegate;
	private final Class<?> viewClass;
	private final @Nullable String namedQueryString;

	public StringBasedItemCollectionQuery(DynamoDbQueryMethod queryMethod, DynamoDbOperations operations,
			ValueExpressionDelegate valueExpressionDelegate) {
		Assert.isTrue(queryMethod.hasAnnotatedQuery(), "Query method must have an @Query annotation: " + queryMethod);

		this.queryMethod = queryMethod;
		this.operations = operations;
		this.query = queryMethod.getQueryAnnotation();
		this.valueExpressionDelegate = valueExpressionDelegate;
		this.viewClass = queryMethod.getResultProcessor().getReturnedType().getDomainType();
		this.namedQueryString = null;
	}

	public StringBasedItemCollectionQuery(DynamoDbQueryMethod queryMethod, DynamoDbOperations operations,
			ValueExpressionDelegate valueExpressionDelegate, String namedQueryString) {
		Assert.hasText(namedQueryString, "Named query string must not be null or empty for method: " + queryMethod);

		this.queryMethod = queryMethod;
		this.operations = operations;
		this.query = queryMethod.getQueryAnnotation();
		this.valueExpressionDelegate = valueExpressionDelegate;
		this.viewClass = queryMethod.getResultProcessor().getReturnedType().getDomainType();
		this.namedQueryString = namedQueryString;
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

		String keyConditionExpression = namedQueryString != null ? namedQueryString
				: query != null ? query.keyConditionExpression() : null;
		if (!StringUtils.hasText(keyConditionExpression)) {
			throw new InvalidDataAccessApiUsageException(
					"ItemCollectionRepository query requires a key condition. Use @Query(keyConditionExpression=...) "
							+ "or a named query property. Method: " + queryMethod);
		}

		var key = bind(keyConditionExpression, accessor);

		Map<String, String> names = expressionNames(key.expression());
		Map<String, Object> values = new LinkedHashMap<>(key.values());

		String filterExpression = null;
		if (query != null && StringUtils.hasText(query.filterExpression())) {
			var filter = bind(query.filterExpression(), accessor);
			filterExpression = filter.expression();
			values.putAll(filter.values());
		}

		applyExpressionValues(values, accessor);

		DynamoDbQueryRequest.Builder builder = DynamoDbQueryRequest.request()
				.withKeyConditionExpression(key.expression())
				.withExpressionAttributeNames(names.isEmpty() ? null : names)
				.withExpressionAttributeValues(values.isEmpty() ? null : values)
				.withConsistentRead(query != null && query.consistentRead());

		if (query != null && StringUtils.hasText(query.indexName())) {
			builder.withIndexName(query.indexName());
		}

		if (StringUtils.hasText(filterExpression)) {
			builder.withFilterExpression(filterExpression);
		}

		Integer pageLimit = query != null && query.limit() > 0 ? query.limit() : null;
		DynamoDbQueryRequest request = builder.build();
		EntityQueryResult<Object> result = (EntityQueryResult<Object>) operations.queryItemCollection(viewClass,
				request, DynamoDbPageRequest.of(pageLimit));

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

	private Map<String, String> expressionNames(String keyConditionExpression) {
		Map<String, String> names = QueryExpressions.expressionNames(query);
		if (namedQueryString == null) {
			return names;
		}
		DynamoDbQuerySpec spec = DynamoDbQuerySpec.forRawKeyCondition(null, keyConditionExpression, names, Map.of());
		return spec.resolveExpressionAttributeNames(keyConditionExpression);
	}

	private void applyExpressionValues(Map<String, Object> values, ParameterAccessor accessor) {
		QueryExpressions.applyExpressionValues(query, valueExpressionDelegate, queryMethod.getParameters(), accessor,
				values);
	}
}
