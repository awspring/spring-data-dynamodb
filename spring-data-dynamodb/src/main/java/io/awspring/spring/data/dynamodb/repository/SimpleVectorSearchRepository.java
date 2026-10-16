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
package io.awspring.spring.data.dynamodb.repository;

import io.awspring.spring.data.dynamodb.core.DynamoDbOperations;
import io.awspring.spring.data.dynamodb.repository.support.DynamoDbEntityInformation;
import io.awspring.spring.data.dynamodb.request.DynamoDbVectorSearchRequest;
import java.util.List;
import org.springframework.util.Assert;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class SimpleVectorSearchRepository<T> implements VectorSearchRepository<T> {

	private final DynamoDbOperations operations;

	private final Class<T> domainType;

	public SimpleVectorSearchRepository(DynamoDbEntityInformation<T, ?> entityInformation,
			DynamoDbOperations operations) {
		Assert.notNull(entityInformation, "entityInformation must not be null");
		Assert.notNull(operations, "operations must not be null");

		this.operations = operations;
		this.domainType = entityInformation.getJavaType();
	}

	@Override
	public List<T> searchNearest(float[] queryVector, int topK) {
		DynamoDbVectorSearchRequest request = DynamoDbVectorSearchRequest.request().withVector(queryVector)
				.withTopK(topK).build();
		return operations.searchVectors(this.domainType, request).getEntity();
	}
}
