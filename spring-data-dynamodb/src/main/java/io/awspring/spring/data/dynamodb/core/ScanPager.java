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
package io.awspring.spring.data.dynamodb.core;

import io.awspring.spring.data.dynamodb.request.DynamoDbScanRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
final class ScanPager<T> {

	private final Class<T> entityClass;
	private final BiFunction<Class<T>, DynamoDbScanRequest, EntityQueryResult<List<T>>> scanFunction;

	ScanPager(Class<T> entityClass,
			BiFunction<Class<T>, DynamoDbScanRequest, EntityQueryResult<List<T>>> scanFunction) {
		this.entityClass = entityClass;
		this.scanFunction = scanFunction;
	}

	List<T> collectAll(DynamoDbScanRequest baseRequest) {
		List<T> all = new ArrayList<>();
		Map<String, Object> exclusiveStartKey = baseRequest.getExclusiveStartKey();

		do {
			DynamoDbScanRequest page = withExclusiveStartKey(baseRequest, exclusiveStartKey);
			EntityQueryResult<List<T>> result = scanFunction.apply(entityClass, page);
			all.addAll(result.getEntity());
			exclusiveStartKey = result.getLastEvaluatedKey();
		}
		while (exclusiveStartKey != null && !exclusiveStartKey.isEmpty());

		return all;
	}

	private static DynamoDbScanRequest withExclusiveStartKey(DynamoDbScanRequest base,
			Map<String, Object> exclusiveStartKey) {
		return DynamoDbScanRequest.Builder.builder().withConsistentRead(base.isConsistentRead())
				.withExclusiveStartKey(exclusiveStartKey)
				.withExpressionAttributeNames(base.getExpressionAttributeNames())
				.withExpressionAttributeValues(base.getExpressionAttributeValues())
				.withFilterExpression(base.getFilterExpression()).withIndexName(base.getIndexName())
				.withLimit(base.getLimit()).withProjectionExpression(base.getProjectionExpression()).build();
	}
}
