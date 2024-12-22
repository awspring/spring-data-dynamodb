package io.awspring.cloud.v3.dynamodb.request;

import java.util.HashMap;
import java.util.Map;

public class DynamoDBPageRequest {

	private Integer limit;
	private Map<String, Object> lastEvaluatedKey = new HashMap<>();


	public static DynamoDBPageRequest of(Integer limit, Map<String,Object> lastEvaluatedKey) {
		DynamoDBPageRequest dynamoDBPageRequest = new DynamoDBPageRequest();
		dynamoDBPageRequest.limit = limit;
		dynamoDBPageRequest.lastEvaluatedKey = lastEvaluatedKey;
		return dynamoDBPageRequest;
	}


	public static DynamoDBPageRequest of(Integer limit) {
		DynamoDBPageRequest dynamoDBPageRequest = new DynamoDBPageRequest();
		dynamoDBPageRequest.limit = limit;
		return dynamoDBPageRequest;
	}

	public Integer getLimit() {
		return limit;
	}

	public Map<String, Object> getLastEvaluatedKey() {
		return lastEvaluatedKey;
	}
}
