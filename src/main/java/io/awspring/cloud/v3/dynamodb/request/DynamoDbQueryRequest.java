package io.awspring.cloud.v3.dynamodb.request;

import java.util.Map;

public class DynamoDbQueryRequest {

	private String filterExpression;
	private String indexName;
	private String 	keyConditionExpression;
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


	public static final class Builder {
		String filterExpression;
		String indexName;
		String 	keyConditionExpression;
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
