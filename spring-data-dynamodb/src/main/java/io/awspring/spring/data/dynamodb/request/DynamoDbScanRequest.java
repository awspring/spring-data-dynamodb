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

import java.util.Map;

/**
 * Options for a low-level DynamoDB {@code Scan} request.
 *
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbScanRequest {

	private boolean consistentRead;
	private Map<String, Object> exclusiveStartKey;

	private Map<String, String> expressionAttributeNames;
	private Map<String, Object> expressionAttributeValues;

	private String filterExpression;

	private String indexName;

	private Integer limit;

	private String projectionExpression;

	public String getFilterExpression() {
		return filterExpression;
	}

	public void setFilterExpression(String filterExpression) {
		this.filterExpression = filterExpression;
	}

	public String getIndexName() {
		return indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public Integer getLimit() {
		return limit;
	}

	public void setLimit(Integer limit) {
		this.limit = limit;
	}

	public String getProjectionExpression() {
		return projectionExpression;
	}

	public void setProjectionExpression(String projectionExpression) {
		this.projectionExpression = projectionExpression;
	}

	public boolean isConsistentRead() {
		return consistentRead;
	}

	public void setConsistentRead(boolean consistentRead) {
		this.consistentRead = consistentRead;
	}

	public Map<String, Object> getExclusiveStartKey() {
		return exclusiveStartKey;
	}

	public void setExclusiveStartKey(Map<String, Object> exclusiveStartKey) {
		this.exclusiveStartKey = exclusiveStartKey;
	}

	public Map<String, String> getExpressionAttributeNames() {
		return expressionAttributeNames;
	}

	public void setExpressionAttributeNames(Map<String, String> expressionAttributeNames) {
		this.expressionAttributeNames = expressionAttributeNames;
	}

	public Map<String, Object> getExpressionAttributeValues() {
		return expressionAttributeValues;
	}

	public void setExpressionAttributeValues(Map<String, Object> expressionAttributeValues) {
		this.expressionAttributeValues = expressionAttributeValues;
	}

	public static final class Builder {
		private boolean consistentRead;
		private Map<String, Object> exclusiveStartKey;
		private Map<String, String> expressionAttributeNames;
		private Map<String, Object> expressionAttributeValues;
		private String filterExpression;
		private String indexName;
		private Integer limit;
		private String projectionExpression;

		private Builder() {
		}

		public static Builder builder() {
			return new Builder();
		}

		public Builder withConsistentRead(boolean consistentRead) {
			this.consistentRead = consistentRead;
			return this;
		}

		public Builder withExclusiveStartKey(Map<String, Object> exclusiveStartKey) {
			this.exclusiveStartKey = exclusiveStartKey;
			return this;
		}

		public Builder withExpressionAttributeNames(Map<String, String> expressionAttributeNames) {
			this.expressionAttributeNames = expressionAttributeNames;
			return this;
		}

		public Builder withExpressionAttributeValues(Map<String, Object> expressionAttributeValues) {
			this.expressionAttributeValues = expressionAttributeValues;
			return this;
		}

		public Builder withFilterExpression(String filterExpression) {
			this.filterExpression = filterExpression;
			return this;
		}

		public Builder withIndexName(String indexName) {
			this.indexName = indexName;
			return this;
		}

		public Builder withLimit(Integer limit) {
			this.limit = limit;
			return this;
		}

		public Builder withProjectionExpression(String projectionExpression) {
			this.projectionExpression = projectionExpression;
			return this;
		}

		public DynamoDbScanRequest build() {
			DynamoDbScanRequest dynamoDbScanRequest = new DynamoDbScanRequest();
			dynamoDbScanRequest.setConsistentRead(consistentRead);
			dynamoDbScanRequest.setExclusiveStartKey(exclusiveStartKey);
			dynamoDbScanRequest.setExpressionAttributeNames(expressionAttributeNames);
			dynamoDbScanRequest.setExpressionAttributeValues(expressionAttributeValues);
			dynamoDbScanRequest.setFilterExpression(filterExpression);
			dynamoDbScanRequest.setIndexName(indexName);
			dynamoDbScanRequest.setLimit(limit);
			dynamoDbScanRequest.setProjectionExpression(projectionExpression);
			return dynamoDbScanRequest;
		}
	}
}
