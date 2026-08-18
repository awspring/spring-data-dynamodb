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
package io.awspring.cloud.dynamodb.request;

import java.util.Map;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbQueryRequest {

	private String filterExpression;
	private String indexName;
	private String keyConditionExpression;
	private Map<String, String> expressionAttributeNames;
	private Map<String, Object> expressionAttributeValues;
	private Boolean scanIndexForward = Boolean.FALSE;
	private Boolean consistentRead = Boolean.FALSE;

	public String getFilterExpression() {
		return filterExpression;
	}

	public String getIndexName() {
		return indexName;
	}

	public String getKeyConditionExpression() {
		return keyConditionExpression;
	}

	public Map<String, String> getExpressionAttributeNames() {
		return expressionAttributeNames;
	}

	public Map<String, Object> getExpressionAttributeValues() {
		return expressionAttributeValues;
	}

	public Boolean getScanIndexForward() {
		return scanIndexForward;
	}

	public Boolean getConsistentRead() {
		return consistentRead;
	}

	public static Builder request() {
		return new Builder();
	}

	public static final class Builder {
		String filterExpression;
		String indexName;
		String keyConditionExpression;
		Map<String, String> expressionAttributeNames;
		Map<String, Object> expressionAttributeValues;
		Boolean scanIndexForward = Boolean.FALSE;
		Boolean consistentRead = Boolean.FALSE;

		private Builder() {
		}

		public static Builder request() {
			return new Builder();
		}

		public Builder withFilterExpression(String filterExpression) {
			this.filterExpression = filterExpression;
			return this;
		}

		public Builder withIndexName(String indexName) {
			this.indexName = indexName;
			return this;
		}

		public Builder withKeyConditionExpression(String keyConditionExpression) {
			this.keyConditionExpression = keyConditionExpression;
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

		public Builder withScanIndexForward(Boolean scanIndexForward) {
			this.scanIndexForward = scanIndexForward;
			return this;
		}

		public Builder withConsistentRead(Boolean consistentRead) {
			this.consistentRead = consistentRead;
			return this;
		}

		public DynamoDbQueryRequest build() {
			DynamoDbQueryRequest dynamoDBQueryRequest = new DynamoDbQueryRequest();
			dynamoDBQueryRequest.filterExpression = this.filterExpression;
			dynamoDBQueryRequest.expressionAttributeNames = this.expressionAttributeNames;
			dynamoDBQueryRequest.expressionAttributeValues = this.expressionAttributeValues;
			dynamoDBQueryRequest.scanIndexForward = this.scanIndexForward;
			dynamoDBQueryRequest.consistentRead = this.consistentRead;
			dynamoDBQueryRequest.indexName = this.indexName;
			dynamoDBQueryRequest.keyConditionExpression = this.keyConditionExpression;
			return dynamoDBQueryRequest;
		}
	}
}
