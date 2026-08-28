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
import io.awspring.spring.data.dynamodb.core.EntityReadResult;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.spring.data.dynamodb.core.mapping.IndexKeySchema;
import io.awspring.spring.data.dynamodb.repository.Query;
import io.awspring.spring.data.dynamodb.repository.Update;
import io.awspring.spring.data.dynamodb.request.DynamoDbUpdateExpressionRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class StringBasedDynamoDbQuery extends AbstractDynamoDbQuery {

	private final @Nullable Query query;
	private final @Nullable Update update;
	private final ValueExpressionDelegate valueExpressionDelegate;
	private final Class<?> domainClass;
	private final MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext;

	@Nullable
	private final String namedQueryString;

	public StringBasedDynamoDbQuery(DynamoDbQueryMethod queryMethod, DynamoDbOperations operations,
			ValueExpressionDelegate valueExpressionDelegate) {
		super(queryMethod, operations);

		Assert.isTrue(queryMethod.hasAnnotatedQuery() || queryMethod.isUpdateQuery(),
				"Query method must have an @Query or @Update annotation: " + queryMethod);

		this.query = queryMethod.getQueryAnnotation();
		this.update = queryMethod.getUpdateAnnotation();
		this.valueExpressionDelegate = valueExpressionDelegate;
		this.domainClass = queryMethod.getResultProcessor().getReturnedType().getDomainType();
		this.mappingContext = operations.getConverter().getMappingContext();
		this.namedQueryString = null;
	}

	public StringBasedDynamoDbQuery(DynamoDbQueryMethod queryMethod, DynamoDbOperations operations,
			ValueExpressionDelegate valueExpressionDelegate, String namedQueryString) {
		super(queryMethod, operations);

		Assert.hasText(namedQueryString, "Named query string must not be null or empty for method: " + queryMethod);

		this.query = queryMethod.getQueryAnnotation();
		this.update = queryMethod.getUpdateAnnotation();
		this.valueExpressionDelegate = valueExpressionDelegate;
		this.domainClass = queryMethod.getResultProcessor().getReturnedType().getDomainType();
		this.mappingContext = operations.getConverter().getMappingContext();
		this.namedQueryString = namedQueryString;
	}

	@Override
	protected Class<?> getDomainClass() {
		return this.domainClass;
	}

	@Override
	protected DynamoDbQuerySpec createQuerySpec(ParameterAccessor accessor) {

		if (namedQueryString != null) {
			var filter = bind(namedQueryString, accessor);
			DynamoDbQuerySpec spec = scanSpec();
			spec.filterFragments().add(filter.expression());
			spec.expressionAttributeNames().putAll(expressionNames());
			spec.expressionAttributeValues().putAll(filter.values());
			applyExpressionValues(spec.expressionAttributeValues(), accessor);
			spec.consistentRead(query != null && query.consistentRead());
			applyExplicitLimit(spec);
			return spec;
		}

		if (StringUtils.hasText(query.keyConditionExpression())) {
			var key = bind(query.keyConditionExpression(), accessor);
			DynamoDbQuerySpec spec = DynamoDbQuerySpec.forRawKeyCondition(query.indexName(), key.expression(),
					expressionNames(), key.values());
			if (StringUtils.hasText(query.filterExpression())) {
				var filter = bind(query.filterExpression(), accessor);
				spec.filterFragments().add(filter.expression());
				spec.expressionAttributeValues().putAll(filter.values());
			}
			applyExpressionValues(spec.expressionAttributeValues(), accessor);
			spec.consistentRead(query.consistentRead());
			applyExplicitLimit(spec);
			return spec;
		}

		if (StringUtils.hasText(query.filterExpression())) {
			var filter = bind(query.filterExpression(), accessor);
			DynamoDbQuerySpec spec = scanSpec();
			spec.filterFragments().add(filter.expression());
			spec.expressionAttributeNames().putAll(expressionNames());
			spec.expressionAttributeValues().putAll(filter.values());
			applyExpressionValues(spec.expressionAttributeValues(), accessor);
			spec.consistentRead(query.consistentRead());
			applyExplicitLimit(spec);
			return spec;
		}

		throw new InvalidDataAccessApiUsageException("@Query method has neither keyConditionExpression() nor "
				+ "filterExpression() set -- nothing to execute (a PartiQL/@Update method takes a different "
				+ "path). Method: " + getQueryMethod());
	}

	private DynamoDbQuerySpec scanSpec() {
		DynamoDbQuerySpec spec = DynamoDbQuerySpec.forScan();
		DynamoDbPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(domainClass);
		if (entity.isSecondaryIndexView()) {
			spec.scanIndexName(entity.getIndexName());
		}
		return spec;
	}

	@Override
	protected UpdateExecution createUpdateRequest(ParameterAccessor accessor) {

		Assert.state(update != null, "Update execution requires an @Update annotation: " + getQueryMethod());

		var boundUpdate = bind(update.updateExpression(), accessor);
		Map<String, Object> values = new LinkedHashMap<>(boundUpdate.values());

		String conditionExpression = null;
		if (StringUtils.hasText(update.conditionExpression())) {
			var condition = bind(update.conditionExpression(), accessor);
			conditionExpression = condition.expression();
			values.putAll(condition.values());
		}
		applyUpdateExpressionValues(values, accessor);

		Map<String, String> names = QueryExpressions.expressionNames(update);
		DynamoDbUpdateExpressionRequest request = DynamoDbUpdateExpressionRequest.Builder.builder()
				.withUpdateExpression(boundUpdate.expression()).withConditionExpression(conditionExpression)
				.withExpressionAttributeNames(names.isEmpty() ? null : names)
				.withExpressionAttributeValues(values.isEmpty() ? null : values).build();

		KeyValues key = resolveKeyValues(accessor);
		return new UpdateExecution(key.partitionKey(), key.sortKey(), request);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected EntityReadResult<List<Object>> executeStatement(ParameterAccessor accessor) {

		Assert.isTrue(query != null && StringUtils.hasText(query.partiQl()),
				"PartiQL execution requires @Query(partiQl=...) to be set. Method: " + getQueryMethod());

		List<Object> positionalValues = positionalValues(accessor);
		return (EntityReadResult<List<Object>>) (EntityReadResult<?>) getOperations().executeStatement(query.partiQl(),
				null, (Class<Object>) domainClass, positionalValues.isEmpty() ? null : positionalValues,
				query.consistentRead());
	}

	private List<Object> positionalValues(ParameterAccessor accessor) {
		List<Object> values = new ArrayList<>();
		int bindableIndex = 0;
		for (Parameter ignored : getQueryMethod().getParameters().getBindableParameters()) {
			values.add(accessor.getBindableValue(bindableIndex));
			bindableIndex++;
		}
		return values;
	}

	private void applyExplicitLimit(DynamoDbQuerySpec spec) {
		if (query != null && query.limit() > 0) {
			spec.explicitLimit(query.limit());
		}
	}

	private QueryExpressions.Bound bind(String rawExpression, ParameterAccessor accessor) {
		return QueryExpressions.bind(valueExpressionDelegate, getQueryMethod().getParameters(), rawExpression,
				accessor);
	}

	private Map<String, String> expressionNames() {
		return QueryExpressions.expressionNames(query);
	}

	private void applyExpressionValues(Map<String, Object> values, ParameterAccessor accessor) {
		QueryExpressions.applyExpressionValues(query, valueExpressionDelegate, getQueryMethod().getParameters(),
				accessor, values);
	}

	private void applyUpdateExpressionValues(Map<String, Object> values, ParameterAccessor accessor) {
		QueryExpressions.applyExpressionValues(update, valueExpressionDelegate, getQueryMethod().getParameters(),
				accessor, values);
	}

	private record KeyValues(Object partitionKey, @Nullable Object sortKey) {
	}

	private KeyValues resolveKeyValues(ParameterAccessor accessor) {

		DynamoDbPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(domainClass);
		IndexKeySchema baseTable = entity.getKeySchema();
		DynamoDbPersistentProperty partitionKey = baseTable.singlePartitionKey();
		DynamoDbPersistentProperty sortKey = baseTable.singleSortKey();

		Assert.state(partitionKey != null,
				"Cannot resolve a base-table partition key for @Update update on " + domainClass.getName());

		Map<String, Object> byName = new HashMap<>();
		int bindableIndex = 0;
		for (Parameter parameter : getQueryMethod().getParameters().getBindableParameters()) {
			Object value = accessor.getBindableValue(bindableIndex);
			parameter.getName().ifPresent(name -> byName.put(name, value));
			bindableIndex++;
		}

		Object partitionValue = requireKeyValue(partitionKey, byName, "partition");
		Object sortValue = sortKey == null ? null : requireKeyValue(sortKey, byName, "sort");
		return new KeyValues(partitionValue, sortValue);
	}

	@Nullable
	private Object requireKeyValue(DynamoDbPersistentProperty keyProperty, Map<String, Object> byName, String role) {
		if (byName.containsKey(keyProperty.getName())) {
			return byName.get(keyProperty.getName());
		}
		String columnName = keyProperty.getColumnName();
		if (columnName != null && byName.containsKey(columnName)) {
			return byName.get(columnName);
		}
		throw new InvalidDataAccessApiUsageException("@Update on " + getQueryMethod() + " needs a @Param "
				+ "parameter named '" + keyProperty.getName() + "'"
				+ (columnName != null && !columnName.equals(keyProperty.getName()) ? " (or '" + columnName + "')" : "")
				+ " supplying the " + role + " key value.");
	}
}
