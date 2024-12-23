package io.awspring.cloud.v3.dynamodb.request;

import java.util.HashMap;
import java.util.Map;

public class DynamoDbPageRequest {

	private Integer limit;
	private Map<String, Object> lastEvaluatedKey = new HashMap<>();


	public static DynamoDbPageRequest of(Integer limit, Map<String,Object> lastEvaluatedKey) {
		DynamoDbPageRequest dynamoDBPageRequest = new DynamoDbPageRequest();
		dynamoDBPageRequest.limit = limit;
		dynamoDBPageRequest.lastEvaluatedKey = lastEvaluatedKey;
		return dynamoDBPageRequest;
	}


	public static DynamoDbPageRequest of(Integer limit) {
		DynamoDbPageRequest dynamoDBPageRequest = new DynamoDbPageRequest();
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
