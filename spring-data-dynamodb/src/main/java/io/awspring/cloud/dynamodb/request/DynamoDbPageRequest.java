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

import java.util.HashMap;
import java.util.Map;

public class DynamoDbPageRequest {

	private Integer limit;
	private Map<String, Object> lastEvaluatedKey = new HashMap<>();

	public static DynamoDbPageRequest of(Integer limit, Map<String, Object> lastEvaluatedKey) {
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
