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
import io.awspring.cloud.dynamodb.core.EntityReadResult;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.cloud.dynamodb.core.mapping.IndexKeySchema;
import io.awspring.cloud.dynamodb.repository.ExpressionName;
import io.awspring.cloud.dynamodb.repository.ExpressionValue;
import io.awspring.cloud.dynamodb.repository.Query;
import io.awspring.cloud.dynamodb.request.DynamoDbUpdateExpressionRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.expression.ValueEvaluationContextProvider;
import org.springframework.data.expression.ValueExpression;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.data.repository.query.ValueExpressionQueryRewriter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class StringBasedDynamoDbQuery extends AbstractDynamoDbQuery {

	private static final Pattern VALUE_TOKEN = Pattern.compile(":([A-Za-z0-9_]+)");

	private final Query query;
	private final ValueExpressionDelegate valueExpressionDelegate;
	private final Class<?> domainClass;
	private final MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext;

	@Nullable
	private final String namedQueryString;

	public StringBasedDynamoDbQuery(DynamoDbQueryMethod queryMethod, DynamoDbOperations operations,
			ValueExpressionDelegate valueExpressionDelegate) {
		super(queryMethod, operations);

		Assert.isTrue(queryMethod.hasAnnotatedQuery(), "Query method must have an @Query annotation: " + queryMethod);

		this.query = queryMethod.getQueryAnnotation();
		this.valueExpressionDelegate = valueExpressionDelegate;
		this.domainClass = queryMethod.getResultProcessor().getReturnedType().getDomainType();
		this.mappingContext = operations.getConverter().getMappingContext();
		this.namedQueryString = null;
	}

	public StringBasedDynamoDbQuery(DynamoDbQueryMethod queryMethod, DynamoDbOperations operations,
			ValueExpressionDelegate valueExpressionDelegate, String namedQueryString) {
		super(queryMethod, operations);

		Assert.hasText(namedQueryString, "Named query string must not be null or empty for method: " + queryMethod);

		this.query = null;
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
			Bound filter = bind(namedQueryString, accessor);
			DynamoDbQuerySpec spec = DynamoDbQuerySpec.forScan();
			spec.filterFragments().add(filter.expression());
			spec.expressionAttributeNames().putAll(expressionNames());
			spec.expressionAttributeValues().putAll(filter.values());
			applyExplicitLimit(spec);
			return spec;
		}

		if (StringUtils.hasText(query.keyConditionExpression())) {
			Bound key = bind(query.keyConditionExpression(), accessor);
			DynamoDbQuerySpec spec = DynamoDbQuerySpec.forRawKeyCondition(query.indexName(), key.expression(),
					expressionNames(), key.values());
			if (StringUtils.hasText(query.filterExpression())) {
				Bound filter = bind(query.filterExpression(), accessor);
				spec.filterFragments().add(filter.expression());
				spec.expressionAttributeValues().putAll(filter.values());
			}
			applyExpressionValues(spec.expressionAttributeValues(), accessor);
			spec.consistentRead(query.consistentRead());
			applyExplicitLimit(spec);
			return spec;
		}

		if (StringUtils.hasText(query.filterExpression())) {
			Bound filter = bind(query.filterExpression(), accessor);
			DynamoDbQuerySpec spec = DynamoDbQuerySpec.forScan();
			spec.filterFragments().add(filter.expression());
			spec.expressionAttributeNames().putAll(expressionNames());
			spec.expressionAttributeValues().putAll(filter.values());
			applyExpressionValues(spec.expressionAttributeValues(), accessor);
			spec.consistentRead(query.consistentRead());
			applyExplicitLimit(spec);
			return spec;
		}

		throw new InvalidDataAccessApiUsageException("@Query method has neither keyConditionExpression() nor "
				+ "filterExpression() set -- nothing to execute (a PartiQL/@Modifying method takes a different "
				+ "path). Method: " + getQueryMethod());
	}

	@Override
	protected ModifyingUpdate createUpdateRequest(ParameterAccessor accessor) {

		Assert.isTrue(query != null && StringUtils.hasText(query.updateExpression()),
				"@Modifying @Query requires updateExpression() to be set. Method: " + getQueryMethod());

		Bound update = bind(query.updateExpression(), accessor);
		Map<String, Object> values = new LinkedHashMap<>(update.values());

		String conditionExpression = null;
		if (StringUtils.hasText(query.conditionExpression())) {
			Bound condition = bind(query.conditionExpression(), accessor);
			conditionExpression = condition.expression();
			values.putAll(condition.values());
		}
		applyExpressionValues(values, accessor);

		Map<String, String> names = expressionNames();
		DynamoDbUpdateExpressionRequest request = DynamoDbUpdateExpressionRequest.Builder.builder()
				.withUpdateExpression(update.expression()).withConditionExpression(conditionExpression)
				.withExpressionAttributeNames(names.isEmpty() ? null : names)
				.withExpressionAttributeValues(values.isEmpty() ? null : values).build();

		KeyValues key = resolveKeyValues(accessor);
		return new ModifyingUpdate(key.partitionKey(), key.sortKey(), request);
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
		if (query != null && query.limit() >= 0) {
			spec.explicitLimit(query.limit());
		}
	}

	private record Bound(String expression, Map<String, Object> values) {
	}

	private Bound bind(String rawExpression, ParameterAccessor accessor) {

		ValueExpressionQueryRewriter.EvaluatingValueExpressionQueryRewriter rewriter = ValueExpressionQueryRewriter
				.of(valueExpressionDelegate, (index, expression) -> "__spel_" + index, (prefix, name) -> ":" + name);

		ValueExpressionQueryRewriter.QueryExpressionEvaluator evaluator = rewriter.parse(rawExpression,
				getQueryMethod().getParameters());

		String expression = evaluator.getQueryString();
		Map<String, Object> values = new LinkedHashMap<>();

		Object[] rawArgs = rawArgs(accessor);
		evaluator.evaluate(rawArgs).forEach((name, value) -> values.put(":" + name, value));

		bindPlainPlaceholders(expression, accessor, values);
		return new Bound(expression, values);
	}

	private void bindPlainPlaceholders(String expression, ParameterAccessor accessor, Map<String, Object> values) {

		Parameters<?, ?> parameters = getQueryMethod().getParameters();
		Map<String, Object> byName = new HashMap<>();
		Map<Integer, Object> byPosition = new HashMap<>();

		int bindableIndex = 0;
		for (Parameter parameter : parameters.getBindableParameters()) {
			Object value = accessor.getBindableValue(bindableIndex);
			byPosition.put(bindableIndex, value);
			parameter.getName().ifPresent(name -> byName.put(name, value));
			bindableIndex++;
		}

		Matcher matcher = VALUE_TOKEN.matcher(expression);
		while (matcher.find()) {
			String token = matcher.group(1);
			String key = ":" + token;
			if (values.containsKey(key)) {
				continue;
			}
			if (byName.containsKey(token)) {
				values.put(key, byName.get(token));
			}
			else if (isAllDigits(token)) {
				int position = Integer.parseInt(token);
				if (byPosition.containsKey(position)) {
					values.put(key, byPosition.get(position));
				}
			}
		}
	}

	private Map<String, String> expressionNames() {
		Map<String, String> names = new LinkedHashMap<>();
		if (query != null) {
			for (ExpressionName name : query.names()) {
				names.put(name.name(), name.value());
			}
		}
		return names;
	}

	private void applyExpressionValues(Map<String, Object> values, ParameterAccessor accessor) {
		if (query == null || query.values().length == 0) {
			return;
		}
		ValueEvaluationContextProvider contextProvider = valueExpressionDelegate
				.createValueContextProvider(getQueryMethod().getParameters());
		Object[] rawArgs = rawArgs(accessor);
		for (ExpressionValue value : query.values()) {
			ValueExpression expression = valueExpressionDelegate.parse(value.value());
			values.put(value.name(), expression.evaluate(contextProvider.getEvaluationContext(rawArgs)));
		}
	}

	private record KeyValues(Object partitionKey, @Nullable Object sortKey) {
	}

	private KeyValues resolveKeyValues(ParameterAccessor accessor) {

		DynamoDbPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(domainClass);
		IndexKeySchema baseTable = entity.getKeySchema();
		DynamoDbPersistentProperty partitionKey = baseTable.singlePartitionKey();
		DynamoDbPersistentProperty sortKey = baseTable.singleSortKey();

		Assert.state(partitionKey != null,
				"Cannot resolve a base-table partition key for @Modifying update on " + domainClass.getName());

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
		throw new InvalidDataAccessApiUsageException("@Modifying @Query on " + getQueryMethod() + " needs a @Param "
				+ "parameter named '" + keyProperty.getName() + "'"
				+ (columnName != null && !columnName.equals(keyProperty.getName()) ? " (or '" + columnName + "')" : "")
				+ " supplying the " + role + " key value.");
	}

	private Object[] rawArgs(ParameterAccessor accessor) {
		return ((DynamoDbParametersParameterAccessor) accessor).getValues();
	}

	private static boolean isAllDigits(String token) {
		for (int i = 0; i < token.length(); i++) {
			if (!Character.isDigit(token.charAt(i))) {
				return false;
			}
		}
		return !token.isEmpty();
	}
}
