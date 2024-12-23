package io.awspring.cloud.v3.dynamodb.request;

import java.util.Map;

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
