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
import io.awspring.spring.data.dynamodb.core.EntityReadResult;
import io.awspring.spring.data.dynamodb.request.DynamoDbPageRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbQueryRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbUpdateExpressionRequest;
import io.awspring.spring.data.dynamodb.request.IndexQueryBuilder;
import java.util.ArrayList;
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

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
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

		if (getQueryMethod().isUpdateQuery()) {
			return executeUpdate(accessor);
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
			return countFor(spec);
		}

		if (getQueryMethod().isExistsQuery()) {
			return existsFor(spec);
		}

		DynamoDbQueryExecution execution = getExecution();
		EntityQueryResult<List<Object>> result = runSpec(spec, getDomainClass(), accessor);
		return execution.execute(result);
	}

	private long countFor(DynamoDbQuerySpec spec) {

		if (spec.requiresScan()) {
			return getOperations().count(getDomainClass(), spec.toScanRequest());
		}

		return getOperations().count(getDomainClass(), toQueryRequest(spec));
	}

	private boolean existsFor(DynamoDbQuerySpec spec) {

		if (spec.requiresScan()) {
			return getOperations().exists(getDomainClass(), spec.toScanRequest());
		}

		return getOperations().exists(getDomainClass(), toQueryRequest(spec));
	}

	@Nullable
	private Object executeUpdate(ParameterAccessor accessor) {
		UpdateExecution update = createUpdateRequest(accessor);
		Object updated = getOperations()
				.update(update.partitionKey(), update.sortKey(), update.request(), getDomainClass()).getEntity();
		return updateResult(updated);
	}

	@Nullable
	private Object updateResult(@Nullable Object updated) {

		Class<?> returnType = getQueryMethod().getDeclaredReturnType();

		if (void.class.equals(returnType) || Void.class.equals(returnType)) {
			return null;
		}
		if (boolean.class.equals(returnType) || Boolean.class.equals(returnType)) {
			return Boolean.TRUE;
		}
		if (int.class.equals(returnType) || Integer.class.equals(returnType)) {
			return 1;
		}
		if (long.class.equals(returnType) || Long.class.equals(returnType)) {
			return 1L;
		}
		return updated;
	}

	protected UpdateExecution createUpdateRequest(ParameterAccessor accessor) {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support @Update execution.");
	}

	protected record UpdateExecution(Object partitionKey, @Nullable Object sortKey,
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

	private EntityQueryResult<List<Object>> runSpec(DynamoDbQuerySpec spec, Class<?> domainClass,
			ParameterAccessor accessor) {

		Map<String, Object> exclusiveStartKey = exclusiveStartKeyFrom(accessor);
		Integer limit = resolveLimit(spec, accessor);

		if (limit != null && spec.filterExpression() != null) {
			return executeFilteredLimited(spec, domainClass, exclusiveStartKey, limit);
		}
		if (limit == null && getQueryMethod().isCollectionQuery()) {
			return executeAllPages(spec, domainClass, exclusiveStartKey);
		}

		EntityQueryResult<List<Object>> result = executePage(spec, domainClass, exclusiveStartKey, limit);

		while (getQueryMethod().isScrollQuery() && result.getEntity().isEmpty()) {
			Map<String, Object> nextStartKey = result.getLastEvaluatedKey();
			if (nextStartKey == null || nextStartKey.isEmpty()) {
				break;
			}
			result = executePage(spec, domainClass, nextStartKey, limit);
		}

		return result;
	}

	private EntityQueryResult<List<Object>> executeAllPages(DynamoDbQuerySpec spec, Class<?> domainClass,
			@Nullable Map<String, Object> exclusiveStartKey) {

		List<Object> items = new ArrayList<>();
		Map<String, Object> nextStartKey = exclusiveStartKey;
		do {
			EntityQueryResult<List<Object>> page = executePage(spec, domainClass, nextStartKey, null);
			items.addAll(page.getEntity());
			nextStartKey = page.getLastEvaluatedKey();
		}
		while (nextStartKey != null && !nextStartKey.isEmpty());
		return EntityQueryResult.of(items, items.size());
	}

	private EntityQueryResult<List<Object>> executeFilteredLimited(DynamoDbQuerySpec spec, Class<?> domainClass,
			@Nullable Map<String, Object> exclusiveStartKey, int limit) {

		List<Object> matches = new ArrayList<>(limit);
		Map<String, Object> nextStartKey = exclusiveStartKey;
		Map<String, Object> lastEvaluatedKey = null;

		do {
			int remaining = limit - matches.size();
			EntityQueryResult<List<Object>> page = executePage(spec, domainClass, nextStartKey, remaining);
			List<Object> pageMatches = page.getEntity();
			if (pageMatches.size() <= remaining) {
				matches.addAll(pageMatches);
			}
			else {
				matches.addAll(pageMatches.subList(0, remaining));
			}
			lastEvaluatedKey = page.getLastEvaluatedKey();
			nextStartKey = lastEvaluatedKey;
		}
		while (matches.size() < limit && nextStartKey != null && !nextStartKey.isEmpty());

		return EntityQueryResult.of(matches, matches.size(), lastEvaluatedKey);
	}

	@SuppressWarnings("unchecked")
	private EntityQueryResult<List<Object>> executePage(DynamoDbQuerySpec spec, Class<?> domainClass,
			@Nullable Map<String, Object> exclusiveStartKey, @Nullable Integer limit) {

		if (spec.requiresScan()) {
			return (EntityQueryResult<List<Object>>) (EntityQueryResult<?>) getOperations().scan(domainClass,
					DynamoDbQuerySpecMapper.toScanRequest(spec, exclusiveStartKey, limit));
		}

		return (EntityQueryResult<List<Object>>) (EntityQueryResult<?>) getOperations().query(
				(Class<Object>) domainClass, toQueryRequest(spec), DynamoDbPageRequest.of(limit, exclusiveStartKey));
	}

	@SuppressWarnings("unchecked")
	private DynamoDbQueryRequest toQueryRequest(DynamoDbQuerySpec spec) {

		if (spec.requiresRawKeyCondition()) {
			return DynamoDbQuerySpecMapper.toRawKeyConditionRequest(spec);
		}

		if (spec.sortConditionIsTemplateColumn()) {
			return DynamoDbQuerySpecMapper.toTemplateSortKeyRequest(spec);
		}

		IndexQueryBuilder<Object> builder = (IndexQueryBuilder<Object>) getOperations()
				.query((Class<Object>) getDomainClass(), spec.indexName());

		return DynamoDbQuerySpecMapper.applyTo(builder, spec, null, null).build();
	}

	@Nullable
	private Map<String, Object> exclusiveStartKeyFrom(ParameterAccessor accessor) {
		ScrollPosition scrollPosition = accessor.getScrollPosition();
		if (scrollPosition == null || scrollPosition.isInitial()) {
			return null;
		}
		if (scrollPosition instanceof KeysetScrollPosition keysetScrollPosition) {
			if (keysetScrollPosition.scrollsBackward()) {
				throw new InvalidDataAccessApiUsageException(
						"DynamoDB pagination only scrolls forward: a backward keyset position would have to re-run the "
								+ "query with an inverted ScanIndexForward, which no DynamoDB resume cursor supports. "
								+ "Use ScrollPosition.keyset() or ScrollPosition.forward(...) -- the position handed "
								+ "out by Window.positionAt(window.size() - 1) is always a forward one. Method: "
								+ getQueryMethod());
			}
			return keysetScrollPosition.getKeys();
		}
		throw new InvalidDataAccessApiUsageException("DynamoDB pagination only supports a keyset ScrollPosition -- got "
				+ scrollPosition.getClass().getSimpleName()
				+ ". Use ScrollPosition.keyset()/forward(...), not an offset position "
				+ "(DynamoDB has no numeric row offset).");
	}

	@Nullable
	private Integer limitFrom(ParameterAccessor accessor) {
		Limit limit = accessor.getLimit();
		return limit != null && limit.isLimited() ? limit.max() : null;
	}

	private DynamoDbQueryExecution getExecution() {

		if (getQueryMethod().isCollectionQuery()) {
			return new DynamoDbQueryExecution.CollectionExecution();
		}
		else if (getQueryMethod().isScrollQuery()) {
			return new DynamoDbQueryExecution.WindowExecution();
		}
		return new DynamoDbQueryExecution.SingleEntityExecution(isLimiting());
	}
}
