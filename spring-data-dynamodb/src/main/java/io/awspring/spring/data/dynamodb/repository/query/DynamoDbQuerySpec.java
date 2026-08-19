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

import io.awspring.spring.data.dynamodb.request.DynamoDbScanRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public final class DynamoDbQuerySpec {

	@Nullable
	private final String indexName;

	private final Map<String, Object> partitionEquals = new LinkedHashMap<>();

	private final List<SortCondition> sortConditions = new ArrayList<>();

	private final List<String> filterFragments = new ArrayList<>();

	private final Map<String, Object> expressionAttributeValues = new LinkedHashMap<>();

	private final Map<String, String> expressionAttributeNames = new LinkedHashMap<>();

	private boolean scanIndexForward = true;

	@Nullable
	private String rawKeyConditionExpression;

	private boolean consistentRead = false;

	@Nullable
	private Integer explicitLimit;

	private boolean sortConditionIsTemplateColumn = false;

	@Nullable
	private String scanIndexName;

	public boolean sortConditionIsTemplateColumn() {
		return sortConditionIsTemplateColumn;
	}

	public void sortConditionIsTemplateColumn(boolean sortConditionIsTemplateColumn) {
		this.sortConditionIsTemplateColumn = sortConditionIsTemplateColumn;
	}

	public void scanIndexName(@Nullable String scanIndexName) {
		this.scanIndexName = scanIndexName;
	}

	DynamoDbQuerySpec(@Nullable String indexName) {
		this.indexName = indexName;
	}

	public static DynamoDbQuerySpec forIndex(String indexName) {
		return new DynamoDbQuerySpec(indexName);
	}

	public static DynamoDbQuerySpec forScan() {
		return new DynamoDbQuerySpec(null);
	}

	public static DynamoDbQuerySpec forRawKeyCondition(String indexName, String keyConditionExpression,
			Map<String, String> names, Map<String, Object> values) {
		DynamoDbQuerySpec spec = new DynamoDbQuerySpec(indexName);
		spec.rawKeyConditionExpression = keyConditionExpression;
		spec.expressionAttributeNames.putAll(names);
		spec.expressionAttributeValues.putAll(values);
		return spec;
	}

	public boolean requiresScan() {
		return indexName == null;
	}

	public boolean requiresRawKeyCondition() {
		return rawKeyConditionExpression != null;
	}

	@Nullable
	public String rawKeyConditionExpression() {
		return rawKeyConditionExpression;
	}

	public boolean consistentRead() {
		return consistentRead;
	}

	public void consistentRead(boolean consistentRead) {
		this.consistentRead = consistentRead;
	}

	@Nullable
	public Integer explicitLimit() {
		return explicitLimit;
	}

	public void explicitLimit(@Nullable Integer explicitLimit) {
		this.explicitLimit = explicitLimit;
	}

	@Nullable
	public String indexName() {
		return indexName;
	}

	public Map<String, Object> partitionEquals() {
		return partitionEquals;
	}

	public List<SortCondition> sortConditions() {
		return sortConditions;
	}

	public List<String> filterFragments() {
		return filterFragments;
	}

	public Map<String, Object> expressionAttributeValues() {
		return expressionAttributeValues;
	}

	public Map<String, String> expressionAttributeNames() {
		return expressionAttributeNames;
	}

	public boolean scanIndexForward() {
		return scanIndexForward;
	}

	public void scanIndexForward(boolean forward) {
		this.scanIndexForward = forward;
	}

	@Nullable
	public String filterExpression() {
		return filterFragments.isEmpty() ? null : String.join(" AND ", filterFragments);
	}

	public record SortCondition(String columnName, Op op, Object value, @Nullable Object rangeEnd) {
		public enum Op{EQ,LT,LE,GT,GE,BETWEEN,BEGINS_WITH}
	}

	public DynamoDbScanRequest toScanRequest() {
		DynamoDbScanRequest.Builder builder = DynamoDbScanRequest.Builder.builder();
		builder.withConsistentRead(consistentRead);
		if (scanIndexName != null) {
			builder.withIndexName(scanIndexName);
		}
		if (!filterFragments.isEmpty()) {
			String filter = filterExpression();
			builder.withFilterExpression(filter);
			Map<String, String> names = resolveExpressionAttributeNames(filter);
			builder.withExpressionAttributeNames(names.isEmpty() ? null : names);
			builder.withExpressionAttributeValues(
					expressionAttributeValues.isEmpty() ? null : expressionAttributeValues);
		}
		return builder.build();
	}

	private static final Pattern NAME_PLACEHOLDER = Pattern.compile("#[A-Za-z0-9_]+");

	public Map<String, String> resolveExpressionAttributeNames(@Nullable String... expressions) {
		Map<String, String> names = new LinkedHashMap<>(expressionAttributeNames);
		for (String expression : expressions) {
			if (expression == null) {
				continue;
			}
			Matcher matcher = NAME_PLACEHOLDER.matcher(expression);
			while (matcher.find()) {
				names.computeIfAbsent(matcher.group(), placeholder -> placeholder.substring(1));
			}
		}
		return names;
	}
}
