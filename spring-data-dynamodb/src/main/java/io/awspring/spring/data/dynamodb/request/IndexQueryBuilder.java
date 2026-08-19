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
package io.awspring.spring.data.dynamodb.request;

import io.awspring.spring.data.dynamodb.core.EntityQueryResult;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.spring.data.dynamodb.core.mapping.IndexKeySchema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import org.springframework.data.mapping.MappingException;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public final class IndexQueryBuilder<T> {

	@FunctionalInterface
	public interface QueryExecutor<T> {
		EntityQueryResult<List<T>> execute(Class<T> entityClass, DynamoDbQueryRequest queryRequest,
				@Nullable DynamoDbPageRequest pageRequest);
	}

	private enum Op {
		EQ("="), LT("<"), LE("<="), GT(">"), GE(">="), BETWEEN("BETWEEN");

		final String symbol;

		Op(String symbol) {
			this.symbol = symbol;
		}
	}

	private final Class<T> entityClass;
	private final String indexName;
	private final IndexKeySchema keySchema;
	private final QueryExecutor<T> executor;

	private final Map<String, Object> partitionEquals = new LinkedHashMap<>();
	private final List<SortCondition> sortConditions = new ArrayList<>();
	private boolean scanIndexForward = true;
	private boolean consistentRead = false;
	@Nullable
	private String filterExpression;
	private final Map<String, String> filterExpressionAttributeNames = new HashMap<>();
	private final Map<String, Object> filterExpressionAttributeValues = new HashMap<>();
	@Nullable
	private Map<String, Object> exclusiveStartKey;
	@Nullable
	private Integer limit;

	private static final class SortCondition {
		final String columnName;
		final Op op;
		@Nullable
		final Object value;
		@Nullable
		final Object rangeEnd;
		final boolean beginsWith;

		SortCondition(String columnName, Op op, @Nullable Object value, @Nullable Object rangeEnd, boolean beginsWith) {
			this.columnName = columnName;
			this.op = op;
			this.value = value;
			this.rangeEnd = rangeEnd;
			this.beginsWith = beginsWith;
		}

		boolean isInequality() {
			return beginsWith || op == Op.BETWEEN || op != Op.EQ;
		}
	}

	public IndexQueryBuilder(Class<T> entityClass, String indexName, IndexKeySchema keySchema,
			QueryExecutor<T> executor) {
		this.entityClass = Objects.requireNonNull(entityClass, "entityClass must not be null");
		this.indexName = Objects.requireNonNull(indexName, "indexName must not be null (use \"\" for the base table)");
		this.keySchema = Objects.requireNonNull(keySchema, "keySchema must not be null");
		this.executor = Objects.requireNonNull(executor, "executor must not be null");
	}

	public IndexQueryBuilder(Class<T> entityClass, String indexName, IndexKeySchema keySchema,
			BiFunction<Class<T>, DynamoDbQueryRequest, EntityQueryResult<List<T>>> executor) {
		this(entityClass, indexName, keySchema,
				(clazz, queryRequest, pageRequest) -> executor.apply(clazz, queryRequest));
	}

	public IndexQueryBuilder<T> partition(String attributeName, Object value) {
		Objects.requireNonNull(attributeName, "attributeName must not be null");
		if (value == null) {
			throw new IllegalArgumentException("Partition-key value for '" + attributeName + "' must not be null");
		}
		if (partitionEquals.containsKey(attributeName)) {
			throw new IllegalStateException("Partition-key equality for '" + attributeName
					+ "' has already been supplied; each partition-key attribute may be supplied at most once");
		}
		partitionEquals.put(attributeName, value);
		return this;
	}

	public IndexQueryBuilder<T> sortEq(String attributeName, Object value) {
		return addSortCondition(attributeName, Op.EQ, value, null, false);
	}

	public IndexQueryBuilder<T> sortLt(String attributeName, Object value) {
		return addSortCondition(attributeName, Op.LT, value, null, false);
	}

	public IndexQueryBuilder<T> sortLe(String attributeName, Object value) {
		return addSortCondition(attributeName, Op.LE, value, null, false);
	}

	public IndexQueryBuilder<T> sortGt(String attributeName, Object value) {
		return addSortCondition(attributeName, Op.GT, value, null, false);
	}

	public IndexQueryBuilder<T> sortGe(String attributeName, Object value) {
		return addSortCondition(attributeName, Op.GE, value, null, false);
	}

	public IndexQueryBuilder<T> sortBetween(String attributeName, Object start, Object end) {
		Objects.requireNonNull(attributeName, "attributeName must not be null");
		if (start == null || end == null) {
			throw new IllegalArgumentException("BETWEEN bounds for '" + attributeName + "' must not be null (start="
					+ start + ", end=" + end + ")");
		}
		sortConditions.add(new SortCondition(attributeName, Op.BETWEEN, start, end, false));
		return this;
	}

	public IndexQueryBuilder<T> sortBeginsWith(String attributeName, Object prefix) {
		return addSortCondition(attributeName, Op.EQ, prefix, null, true);
	}

	private IndexQueryBuilder<T> addSortCondition(String attributeName, Op op, Object value, @Nullable Object rangeEnd,
			boolean beginsWith) {
		Objects.requireNonNull(attributeName, "attributeName must not be null");
		if (value == null) {
			throw new IllegalArgumentException("Sort-key value for '" + attributeName + "' must not be null");
		}
		sortConditions.add(new SortCondition(attributeName, op, value, rangeEnd, beginsWith));
		return this;
	}

	public IndexQueryBuilder<T> scanIndexForward(boolean forward) {
		this.scanIndexForward = forward;
		return this;
	}

	public IndexQueryBuilder<T> consistentRead(boolean consistentRead) {
		this.consistentRead = consistentRead;
		return this;
	}

	public IndexQueryBuilder<T> filterExpression(@Nullable String filterExpression) {
		this.filterExpression = filterExpression;
		this.filterExpressionAttributeNames.clear();
		this.filterExpressionAttributeValues.clear();
		return this;
	}

	public IndexQueryBuilder<T> filterExpression(@Nullable String filterExpression, Map<String, String> names,
			Map<String, Object> values) {
		Objects.requireNonNull(names, "names must not be null (pass an empty map if the filter uses no #name tokens)");
		Objects.requireNonNull(values,
				"values must not be null (pass an empty map if the filter uses no :value tokens)");
		this.filterExpression = filterExpression;
		this.filterExpressionAttributeNames.clear();
		this.filterExpressionAttributeValues.clear();
		this.filterExpressionAttributeNames.putAll(names);
		this.filterExpressionAttributeValues.putAll(values);
		return this;
	}

	public IndexQueryBuilder<T> exclusiveStartKey(@Nullable Map<String, Object> exclusiveStartKey) {
		this.exclusiveStartKey = exclusiveStartKey;
		return this;
	}

	public IndexQueryBuilder<T> limit(@Nullable Integer limit) {
		if (limit != null && limit <= 0) {
			throw new IllegalArgumentException("limit must be positive (got " + limit + ")");
		}
		this.limit = limit;
		return this;
	}

	public EntityQueryResult<List<T>> execute() {
		DynamoDbPageRequest pageRequest;
		if (exclusiveStartKey != null) {
			pageRequest = DynamoDbPageRequest.of(limit, exclusiveStartKey);
		}
		else if (limit != null) {
			pageRequest = DynamoDbPageRequest.of(limit);
		}
		else {
			pageRequest = null;
		}
		return executor.execute(entityClass, build(), pageRequest);
	}

	public DynamoDbQueryRequest build() {
		validatePartitionKeys();
		validateSortConditionOrder();

		Map<String, String> expressionAttributeNames = new LinkedHashMap<>();
		Map<String, Object> expressionAttributeValues = new LinkedHashMap<>();
		StringBuilder keyConditionExpression = new StringBuilder();
		int placeholderIndex = 0;

		for (DynamoDbPersistentProperty partitionKeyProperty : keySchema.partitionKeys()) {
			String columnName = partitionKeyProperty.getColumnName();
			Object value = partitionEquals.get(columnName);
			String namePlaceholder = "#k" + placeholderIndex;
			String valuePlaceholder = ":k" + placeholderIndex;
			expressionAttributeNames.put(namePlaceholder, columnName);
			expressionAttributeValues.put(valuePlaceholder, value);
			if (keyConditionExpression.length() > 0) {
				keyConditionExpression.append(" AND ");
			}
			keyConditionExpression.append(namePlaceholder).append(" = ").append(valuePlaceholder);
			placeholderIndex++;
		}

		for (SortCondition condition : sortConditions) {
			String namePlaceholder = "#k" + placeholderIndex;
			expressionAttributeNames.put(namePlaceholder, condition.columnName);
			keyConditionExpression.append(" AND ");

			if (condition.beginsWith) {
				String valuePlaceholder = ":k" + placeholderIndex;
				expressionAttributeValues.put(valuePlaceholder, condition.value);
				keyConditionExpression.append("begins_with(").append(namePlaceholder).append(", ")
						.append(valuePlaceholder).append(")");
			}
			else if (condition.op == Op.BETWEEN) {
				String startPlaceholder = ":k" + placeholderIndex + "a";
				String endPlaceholder = ":k" + placeholderIndex + "b";
				expressionAttributeValues.put(startPlaceholder, condition.value);
				expressionAttributeValues.put(endPlaceholder, condition.rangeEnd);
				keyConditionExpression.append(namePlaceholder).append(" BETWEEN ").append(startPlaceholder)
						.append(" AND ").append(endPlaceholder);
			}
			else {
				String valuePlaceholder = ":k" + placeholderIndex;
				expressionAttributeValues.put(valuePlaceholder, condition.value);
				keyConditionExpression.append(namePlaceholder).append(" ").append(condition.op.symbol).append(" ")
						.append(valuePlaceholder);
			}
			placeholderIndex++;
		}

		DynamoDbQueryRequest.Builder builder = DynamoDbQueryRequest.Builder.request()
				.withIndexName(indexName.isEmpty() ? null : indexName)
				.withKeyConditionExpression(keyConditionExpression.toString()).withScanIndexForward(scanIndexForward)
				.withConsistentRead(consistentRead);
		if (filterExpression != null) {
			builder.withFilterExpression(filterExpression);
			mergeFilterPlaceholders(expressionAttributeNames, expressionAttributeValues);
		}
		builder.withExpressionAttributeNames(expressionAttributeNames);
		builder.withExpressionAttributeValues(expressionAttributeValues);
		return builder.build();
	}

	private void mergeFilterPlaceholders(Map<String, String> expressionAttributeNames,
			Map<String, Object> expressionAttributeValues) {
		for (Map.Entry<String, String> entry : filterExpressionAttributeNames.entrySet()) {
			if (expressionAttributeNames.containsKey(entry.getKey())) {
				throw new MappingException("Filter-expression name placeholder '" + entry.getKey()
						+ "' collides with a key-condition placeholder auto-generated by IndexQueryBuilder "
						+ "(key placeholders use the '#k<n>' prefix). Rename filter placeholders to a different prefix "
						+ "(for example '#p<n>' or '#f<n>').");
			}
			expressionAttributeNames.put(entry.getKey(), entry.getValue());
		}
		for (Map.Entry<String, Object> entry : filterExpressionAttributeValues.entrySet()) {
			if (expressionAttributeValues.containsKey(entry.getKey())) {
				throw new MappingException("Filter-expression value placeholder '" + entry.getKey()
						+ "' collides with a key-condition placeholder auto-generated by IndexQueryBuilder "
						+ "(key placeholders use the ':k<n>' prefix). Rename filter placeholders to a different prefix "
						+ "(for example ':p<n>' or ':f<n>').");
			}
			expressionAttributeValues.put(entry.getKey(), entry.getValue());
		}
	}

	private void validatePartitionKeys() {
		List<String> missing = new ArrayList<>();
		Set<String> knownPartitionColumns = new HashSet<>();
		for (DynamoDbPersistentProperty p : keySchema.partitionKeys()) {
			knownPartitionColumns.add(p.getColumnName());
			if (!partitionEquals.containsKey(p.getColumnName())) {
				missing.add(p.getColumnName());
			}
		}
		if (!missing.isEmpty()) {
			throw new MappingException("Query on index '" + labelForIndex() + "' is missing required partition-key "
					+ "equality condition(s) for: " + missing
					+ ". All partition-key attributes must be supplied with equality.");
		}
		for (String suppliedColumn : partitionEquals.keySet()) {
			if (!knownPartitionColumns.contains(suppliedColumn)) {
				throw new MappingException("'" + suppliedColumn + "' is not a partition-key attribute of index '"
						+ labelForIndex() + "'; expected one of " + knownPartitionColumns);
			}
		}
	}

	private void validateSortConditionOrder() {
		List<String> orderedSortColumns = new ArrayList<>();
		for (DynamoDbPersistentProperty p : keySchema.sortKeys()) {
			orderedSortColumns.add(p.getColumnName());
		}

		Set<String> seenColumns = new HashSet<>();
		for (int i = 0; i < sortConditions.size(); i++) {
			SortCondition condition = sortConditions.get(i);
			if (i >= orderedSortColumns.size() || !orderedSortColumns.get(i).equals(condition.columnName)) {
				throw new MappingException("Sort-key conditions on index '" + labelForIndex()
						+ "' must be supplied left-to-right with no gaps, in declared order " + orderedSortColumns
						+ "; got '" + condition.columnName + "' at position " + i + ".");
			}
			if (!seenColumns.add(condition.columnName)) {
				throw new MappingException("Sort-key column '" + condition.columnName + "' on index '" + labelForIndex()
						+ "' has more than one condition; each sort-key column may be constrained "
						+ "by at most one condition per query.");
			}
			boolean isLast = i == sortConditions.size() - 1;
			if (condition.isInequality() && !isLast) {
				throw new MappingException("Sort-key condition on '" + condition.columnName + "' (index '"
						+ labelForIndex() + "') is an inequality/begins_with/BETWEEN condition, which must be the "
						+ "LAST sort-key condition in the query.");
			}
		}
	}

	private String labelForIndex() {
		return indexName.isEmpty() ? "<base table>" : indexName;
	}
}
