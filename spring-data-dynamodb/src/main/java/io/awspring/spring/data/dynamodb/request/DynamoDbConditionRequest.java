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
 * Condition expression and values for a DynamoDB write.
 *
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbConditionRequest {

	private String conditionExpression;
	private Map<String, String> expressionAttributeNames;
	private Map<String, Object> expressionAttributeValues;

	public String getConditionExpression() {
		return conditionExpression;
	}

	public Map<String, String> getExpressionAttributeNames() {
		return expressionAttributeNames;
	}

	public Map<String, Object> getExpressionAttributeValues() {
		return expressionAttributeValues;
	}

	public static final class Builder {
		String conditionExpression;
		Map<String, String> expressionAttributeNames;
		Map<String, Object> expressionAttributeValues;

		private Builder() {
		}

		public static Builder request() {
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

		public DynamoDbConditionRequest build() {
			DynamoDbConditionRequest dynamoDBConditionRequest = new DynamoDbConditionRequest();
			dynamoDBConditionRequest.expressionAttributeValues = this.expressionAttributeValues;
			dynamoDBConditionRequest.conditionExpression = this.conditionExpression;
			dynamoDBConditionRequest.expressionAttributeNames = this.expressionAttributeNames;
			return dynamoDBConditionRequest;
		}
	}
}
