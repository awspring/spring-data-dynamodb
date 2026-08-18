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

import io.awspring.cloud.dynamodb.repository.query.DynamoDbQuerySpec.SortCondition;
import io.awspring.cloud.dynamodb.request.DynamoDbQueryRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbScanRequest;
import io.awspring.cloud.dynamodb.request.IndexQueryBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/**
 * Maps a {@link DynamoDbQuerySpec} onto the request objects the {@code core} layer understands.
 * <p>
 * A spec is produced by either the derived-query creator or the {@code @Query} escape hatch; this mapper is the single
 * place that turns it into a {@link DynamoDbQueryRequest}, a {@link DynamoDbScanRequest} or a populated
 * {@link IndexQueryBuilder}, keeping the execution classes free of request-assembly detail.
 *
 * @author Matej Nedic
 * @since 1.0.0
 */
/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public final class DynamoDbQuerySpecMapper {

	private static final String TEMPLATE_NAME_PREFIX = "#tk";
	private static final String TEMPLATE_VALUE_PREFIX = ":tk";

	private DynamoDbQuerySpecMapper() {
	}

	public static DynamoDbQueryRequest toRawKeyConditionRequest(DynamoDbQuerySpec spec) {

		Assert.state(spec.requiresRawKeyCondition(),
				"Spec has no raw key-condition expression -- cannot map it to a raw Query request");

		Map<String, String> names = spec.resolveExpressionAttributeNames(spec.rawKeyConditionExpression(),
				spec.filterExpression());

		return DynamoDbQueryRequest.Builder.request().withIndexName(indexNameOrNull(spec))
				.withKeyConditionExpression(spec.rawKeyConditionExpression())
				.withFilterExpression(spec.filterExpression()).withExpressionAttributeNames(emptyToNull(names))
				.withExpressionAttributeValues(emptyToNull(spec.expressionAttributeValues()))
				.withScanIndexForward(spec.scanIndexForward()).withConsistentRead(spec.consistentRead()).build();
	}

	public static DynamoDbQueryRequest toTemplateSortKeyRequest(DynamoDbQuerySpec spec) {

		Map<String, String> names = spec.resolveExpressionAttributeNames(spec.filterExpression());
		Map<String, Object> values = new LinkedHashMap<>(spec.expressionAttributeValues());
		List<String> conjuncts = new ArrayList<>();
		int placeholderIndex = 0;

		for (Map.Entry<String, Object> partitionEquality : spec.partitionEquals().entrySet()) {
			String namePlaceholder = TEMPLATE_NAME_PREFIX + placeholderIndex;
			String valuePlaceholder = TEMPLATE_VALUE_PREFIX + placeholderIndex;
			names.put(namePlaceholder, partitionEquality.getKey());
			values.put(valuePlaceholder, partitionEquality.getValue());
			conjuncts.add(namePlaceholder + " = " + valuePlaceholder);
			placeholderIndex++;
		}

		for (SortCondition condition : spec.sortConditions()) {
			String namePlaceholder = TEMPLATE_NAME_PREFIX + placeholderIndex;
			String valuePlaceholder = TEMPLATE_VALUE_PREFIX + placeholderIndex;
			names.put(namePlaceholder, condition.columnName());
			values.put(valuePlaceholder, condition.value());
			conjuncts.add(condition.op() == SortCondition.Op.BEGINS_WITH
					? "begins_with(" + namePlaceholder + ", " + valuePlaceholder + ")"
					: namePlaceholder + " = " + valuePlaceholder);
			placeholderIndex++;
		}

		return DynamoDbQueryRequest.Builder.request().withIndexName(indexNameOrNull(spec))
				.withKeyConditionExpression(String.join(" AND ", conjuncts))
				.withFilterExpression(spec.filterExpression()).withExpressionAttributeNames(names)
				.withExpressionAttributeValues(values).withScanIndexForward(spec.scanIndexForward())
				.withConsistentRead(spec.consistentRead()).build();
	}

	public static DynamoDbScanRequest toScanRequest(DynamoDbQuerySpec spec,
			@Nullable Map<String, Object> exclusiveStartKey, @Nullable Integer limit) {

		DynamoDbScanRequest baseRequest = spec.toScanRequest();

		return DynamoDbScanRequest.Builder.builder().withConsistentRead(baseRequest.isConsistentRead())
				.withExpressionAttributeNames(baseRequest.getExpressionAttributeNames())
				.withExpressionAttributeValues(baseRequest.getExpressionAttributeValues())
				.withFilterExpression(baseRequest.getFilterExpression()).withIndexName(baseRequest.getIndexName())
				.withProjectionExpression(baseRequest.getProjectionExpression())
				.withExclusiveStartKey(exclusiveStartKey).withLimit(limit).build();
	}

	public static <T> IndexQueryBuilder<T> applyTo(IndexQueryBuilder<T> builder, DynamoDbQuerySpec spec,
			@Nullable Map<String, Object> exclusiveStartKey, @Nullable Integer limit) {

		for (Map.Entry<String, Object> partitionEquality : spec.partitionEquals().entrySet()) {
			builder.partition(partitionEquality.getKey(), partitionEquality.getValue());
		}

		for (SortCondition condition : spec.sortConditions()) {
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
			builder.filterExpression(spec.filterExpression(),
					spec.resolveExpressionAttributeNames(spec.filterExpression()), spec.expressionAttributeValues());
		}

		builder.scanIndexForward(spec.scanIndexForward());
		builder.consistentRead(spec.consistentRead());
		builder.exclusiveStartKey(exclusiveStartKey);
		builder.limit(limit);

		return builder;
	}

	@Nullable
	private static String indexNameOrNull(DynamoDbQuerySpec spec) {
		String indexName = spec.indexName();
		return (indexName == null || indexName.isEmpty()) ? null : indexName;
	}

	@Nullable
	private static <K, V> Map<K, V> emptyToNull(Map<K, V> map) {
		return map.isEmpty() ? null : map;
	}
}
