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

public class DynamoDbUpdateExpressionRequest {

	private String conditionExpression;
	private Map<String, String> expressionAttributeNames;
	private Map<String, Object> expressionAttributeValues;
	private String updateExpression;

	public String getConditionExpression() {
		return conditionExpression;
	}

	public Map<String, String> getExpressionAttributeNames() {
		return expressionAttributeNames;
	}

	public Map<String, Object> getExpressionAttributeValues() {
		return expressionAttributeValues;
	}

	public String getUpdateExpression() {
		return updateExpression;
	}

	public static final class Builder {
		String conditionExpression;
		Map<String, String> expressionAttributeNames;
		Map<String, Object> expressionAttributeValues;
		String updateExpression;

		private Builder() {
		}

		public static Builder builder() {
			return new Builder();
		}

		public Builder withConditionExpression(String conditionExpression) {
			this.conditionExpression = conditionExpression;
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

		public Builder withUpdateExpression(String updateExpression) {
			this.updateExpression = updateExpression;
			return this;
		}

		public DynamoDbUpdateExpressionRequest build() {
			DynamoDbUpdateExpressionRequest dynamoDBUpdateExpressionRequest = new DynamoDbUpdateExpressionRequest();
			dynamoDBUpdateExpressionRequest.conditionExpression = this.conditionExpression;
			dynamoDBUpdateExpressionRequest.expressionAttributeNames = this.expressionAttributeNames;
			dynamoDBUpdateExpressionRequest.updateExpression = this.updateExpression;
			dynamoDBUpdateExpressionRequest.expressionAttributeValues = this.expressionAttributeValues;
			return dynamoDBUpdateExpressionRequest;
		}
	}
}
