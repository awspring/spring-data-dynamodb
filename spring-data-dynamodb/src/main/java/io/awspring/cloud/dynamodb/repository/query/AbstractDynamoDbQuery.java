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
import io.awspring.cloud.dynamodb.core.EntityReadResult;
import io.awspring.cloud.dynamodb.request.DynamoDbPageRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbQueryRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbScanRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbUpdateExpressionRequest;
import io.awspring.cloud.dynamodb.request.IndexQueryBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.RepositoryQuery;

public abstract class AbstractDynamoDbQuery implements RepositoryQuery {

	private final DynamoDbQueryMethod queryMethod;
	private final DynamoDbOperations operations;

	protected AbstractDynamoDbQuery(DynamoDbQueryMethod queryMethod, DynamoDbOperations operations) {
		this.queryMethod = queryMethod;
		this.operations = operations;
	}

	@Override
	public DynamoDbQueryMethod getQueryMethod() {
		return this.queryMethod;
	}

	protected DynamoDbOperations getOperations() {
		return this.operations;
	}

	protected abstract DynamoDbQuerySpec createQuerySpec(ParameterAccessor accessor);

	protected abstract Class<?> getDomainClass();

	protected boolean isLimiting() {
		return false;
	}

	@Nullable
	protected Integer derivedResultLimit() {
		return null;
	}

	@Nullable
	private Integer resolveLimit(DynamoDbQuerySpec spec, ParameterAccessor accessor) {
		if (spec.explicitLimit() != null) {
			return spec.explicitLimit();
		}
		if (derivedResultLimit() != null) {
			return derivedResultLimit();
		}
		return limitFrom(accessor);
	}

	@Override
	@Nullable
	public Object execute(Object[] parameters) {

		DynamoDbParametersParameterAccessor accessor = new DynamoDbParametersParameterAccessor(getQueryMethod(),
				parameters);

		if (getQueryMethod().isModifyingQuery()) {
			return executeModifying(accessor);
		}

		if (getQueryMethod().isPartiQlQuery()) {
			return executePartiQl(accessor);
		}

		DynamoDbQuerySpec spec = createQuerySpec(accessor);

		if (spec.requiresScan() && !getQueryMethod().allowsScan()) {
			throw new InvalidDataAccessApiUsageException(
					"Query method " + getQueryMethod() + " requires a full-table Scan (no index can serve it as a "
							+ "Query) but is not annotated @AllowScan.");
		}

		if (getQueryMethod().isCountQuery()) {
			return getOperations().count(getDomainClass(), spec.toScanRequest());
		}

		if (getQueryMethod().isExistsQuery()) {
			return getOperations().exists(getDomainClass(), spec.toScanRequest());
		}

		DynamoDbQueryExecution execution = getExecution(spec, getDomainClass());
		EntityQueryResult<List<Object>> result = runSpec(spec, getDomainClass(), accessor);
		return execution.execute(result);
	}

	@Nullable
	private Object executeModifying(ParameterAccessor accessor) {
		ModifyingUpdate update = createUpdateRequest(accessor);
		return getOperations().update(update.partitionKey(), update.sortKey(), update.request(), getDomainClass())
				.getEntity();
	}

	protected ModifyingUpdate createUpdateRequest(ParameterAccessor accessor) {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support @Modifying execution.");
	}

	protected record ModifyingUpdate(Object partitionKey, @Nullable Object sortKey,
			DynamoDbUpdateExpressionRequest request) {
	}

	@Nullable
	private Object executePartiQl(ParameterAccessor accessor) {

		if (getQueryMethod().isScrollQuery()) {
			throw new UnsupportedOperationException("PartiQL pagination (Window<T>) is not yet implemented. "
					+ "Use a List<T>/Optional<T>/single-entity return type for a "
					+ "@Query(partiQl=...) method for now. Method: " + getQueryMethod());
		}

		EntityReadResult<List<Object>> result = executeStatement(accessor);
		List<Object> items = result.getEntity();

		if (getQueryMethod().isCollectionQuery()) {
			return items;
		}
		if (items.isEmpty()) {
			return null;
		}
		if (items.size() > 1) {
			throw new IncorrectResultSizeDataAccessException(1, items.size());
		}
		return items.get(0);
	}

	protected EntityReadResult<List<Object>> executeStatement(ParameterAccessor accessor) {
		throw new UnsupportedOperationException(
				getClass().getSimpleName() + " does not support @Query(partiQl=...) execution.");
	}

	@SuppressWarnings("unchecked")
	private EntityQueryResult<List<Object>> runSpec(DynamoDbQuerySpec spec, Class<?> domainClass,
			ParameterAccessor accessor) {

		Map<String, Object> exclusiveStartKey = exclusiveStartKeyFrom(accessor);
		Integer limit = resolveLimit(spec, accessor);

		if (spec.requiresRawKeyCondition()) {
			DynamoDbQueryRequest queryRequest = DynamoDbQueryRequest.Builder.request().withIndexName(spec.indexName())
					.withKeyConditionExpression(spec.rawKeyConditionExpression())
					.withFilterExpression(spec.filterExpression())
					.withExpressionAttributeNames(
							spec.expressionAttributeNames().isEmpty() ? null : spec.expressionAttributeNames())
					.withExpressionAttributeValues(
							spec.expressionAttributeValues().isEmpty() ? null : spec.expressionAttributeValues())
					.withScanIndexForward(spec.scanIndexForward()).withConsistentRead(spec.consistentRead()).build();
			DynamoDbPageRequest pageRequest = DynamoDbPageRequest.of(limit, exclusiveStartKey);
			return (EntityQueryResult<List<Object>>) (EntityQueryResult<?>) getOperations()
					.query((Class<Object>) domainClass, queryRequest, pageRequest);
		}

		if (spec.requiresScan()) {
			DynamoDbScanRequest baseRequest = spec.toScanRequest();
			DynamoDbScanRequest scanRequest = DynamoDbScanRequest.Builder.builder()
					.withConsistentRead(baseRequest.isConsistentRead())
					.withExpressionAttributeNames(baseRequest.getExpressionAttributeNames())
					.withExpressionAttributeValues(baseRequest.getExpressionAttributeValues())
					.withFilterExpression(baseRequest.getFilterExpression()).withIndexName(baseRequest.getIndexName())
					.withProjectionExpression(baseRequest.getProjectionExpression())
					.withExclusiveStartKey(exclusiveStartKey).withLimit(limit).build();
			return (EntityQueryResult<List<Object>>) (EntityQueryResult<?>) getOperations().scan(domainClass,
					scanRequest);
		}

		if (spec.sortConditionIsTemplateColumn()) {
			return (EntityQueryResult<List<Object>>) (EntityQueryResult<?>) getOperations().query(
					(Class<Object>) domainClass, buildTemplateSortKeyRequest(spec),
					DynamoDbPageRequest.of(limit, exclusiveStartKey));
		}

		IndexQueryBuilder<Object> builder = (IndexQueryBuilder<Object>) getOperations()
				.query((Class<Object>) domainClass, spec.indexName());

		for (Map.Entry<String, Object> partitionEquality : spec.partitionEquals().entrySet()) {
			builder.partition(partitionEquality.getKey(), partitionEquality.getValue());
		}

		for (DynamoDbQuerySpec.SortCondition condition : spec.sortConditions()) {
			switch (condition.op()) {
			case EQ -> builder.sortEq(condition.columnName(), condition.value());
			case LT -> builder.sortLt(condition.columnName(), condition.value());
			case LE -> builder.sortLe(condition.columnName(), condition.value());
			case GT -> builder.sortGt(condition.columnName(), condition.value());
			case GE -> builder.sortGe(condition.columnName(), condition.value());
			case BETWEEN -> builder.sortBetween(condition.columnName(), condition.value(), condition.rangeEnd());
			case BEGINS_WITH -> builder.sortBeginsWith(condition.columnName(), condition.value());
			}
		}

		if (spec.filterExpression() != null) {
			builder.filterExpression(spec.filterExpression(), spec.expressionAttributeNames(),
					spec.expressionAttributeValues());
		}

		builder.scanIndexForward(spec.scanIndexForward());
		builder.exclusiveStartKey(exclusiveStartKey);
		builder.limit(limit);

		return (EntityQueryResult<List<Object>>) (EntityQueryResult<?>) builder.execute();
	}

	private DynamoDbQueryRequest buildTemplateSortKeyRequest(DynamoDbQuerySpec spec) {

		Map<String, String> names = new LinkedHashMap<>(spec.expressionAttributeNames());
		Map<String, Object> values = new LinkedHashMap<>(spec.expressionAttributeValues());
		StringBuilder keyCondition = new StringBuilder();
		int i = 0;

		for (Map.Entry<String, Object> partitionEquality : spec.partitionEquals().entrySet()) {
			String namePlaceholder = "#tk" + i;
			String valuePlaceholder = ":tk" + i;
			names.put(namePlaceholder, partitionEquality.getKey());
			values.put(valuePlaceholder, partitionEquality.getValue());
			keyCondition.append(namePlaceholder).append(" = ").append(valuePlaceholder);
			i++;
		}

		for (DynamoDbQuerySpec.SortCondition condition : spec.sortConditions()) {
			String namePlaceholder = "#tk" + i;
			String valuePlaceholder = ":tk" + i;
			names.put(namePlaceholder, condition.columnName());
			values.put(valuePlaceholder, condition.value());
			keyCondition.append(" AND ");
			if (condition.op() == DynamoDbQuerySpec.SortCondition.Op.BEGINS_WITH) {
				keyCondition.append("begins_with(").append(namePlaceholder).append(", ").append(valuePlaceholder)
						.append(")");
			}
			else {
				keyCondition.append(namePlaceholder).append(" = ").append(valuePlaceholder);
			}
			i++;
		}

		return DynamoDbQueryRequest.Builder.request()
				.withIndexName(spec.indexName().isEmpty() ? null : spec.indexName())
				.withKeyConditionExpression(keyCondition.toString()).withFilterExpression(spec.filterExpression())
				.withExpressionAttributeNames(names).withExpressionAttributeValues(values)
				.withScanIndexForward(spec.scanIndexForward()).withConsistentRead(spec.consistentRead()).build();
	}

	@Nullable
	private Map<String, Object> exclusiveStartKeyFrom(ParameterAccessor accessor) {
		ScrollPosition scrollPosition = accessor.getScrollPosition();
		if (scrollPosition == null || scrollPosition.isInitial()) {
			return null;
		}
		if (scrollPosition instanceof KeysetScrollPosition keysetScrollPosition) {
			return (Map<String, Object>) keysetScrollPosition.getKeys();
		}
		throw new InvalidDataAccessApiUsageException("DynamoDB pagination only supports a keyset ScrollPosition -- got "
				+ scrollPosition.getClass().getSimpleName()
				+ ". Use ScrollPosition.keyset()/forward(...)/backward(...), not an offset position "
				+ "(DynamoDB has no numeric row offset).");
	}

	@Nullable
	private Integer limitFrom(ParameterAccessor accessor) {
		Limit limit = accessor.getLimit();
		return limit != null && limit.isLimited() ? limit.max() : null;
	}

	private DynamoDbQueryExecution getExecution(DynamoDbQuerySpec spec, Class<?> domainClass) {

		if (getQueryMethod().isCollectionQuery()) {
			return new DynamoDbQueryExecution.CollectionExecution();
		}
		else if (getQueryMethod().isScrollQuery()) {
			return new DynamoDbQueryExecution.WindowExecution();
		}
		return new DynamoDbQueryExecution.SingleEntityExecution(isLimiting());
	}
}
