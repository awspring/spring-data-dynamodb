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
package io.awspring.spring.data.dynamodb.repository.query;

import io.awspring.spring.data.dynamodb.core.DynamoDbOperations;
import io.awspring.spring.data.dynamodb.core.EntityQueryResult;
import io.awspring.spring.data.dynamodb.repository.Filter;
import io.awspring.spring.data.dynamodb.request.DynamoDbVectorSearchRequest;
import java.lang.reflect.Parameter;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public abstract class AbstractVectorSearchQuery implements RepositoryQuery {

	private final DynamoDbQueryMethod queryMethod;
	private final DynamoDbOperations operations;
	private final Class<?> domainClass;
	private final int vectorParameterIndex;
	private final DynamoDbQueryExecution execution;

	protected AbstractVectorSearchQuery(DynamoDbQueryMethod queryMethod, DynamoDbOperations operations) {
		Assert.notNull(queryMethod, "queryMethod must not be null");
		Assert.notNull(operations, "operations must not be null");

		this.queryMethod = queryMethod;
		this.operations = operations;
		this.domainClass = queryMethod.getResultProcessor().getReturnedType().getDomainType();
		this.vectorParameterIndex = vectorParameterIndex(queryMethod);
		this.execution = execution(queryMethod);
	}

	@Override
	public DynamoDbQueryMethod getQueryMethod() {
		return this.queryMethod;
	}

	@Override
	@Nullable
	@SuppressWarnings("unchecked")
	public Object execute(Object[] parameters) {
		DynamoDbVectorSearchRequest.Builder builder = DynamoDbVectorSearchRequest.request()
				.withVector((float[]) parameters[this.vectorParameterIndex]).withTopK(topK());
		String indexName = indexName();
		if (StringUtils.hasText(indexName)) {
			builder.withIndexName(indexName);
		}
		applySearchCondition(builder, parameters);

		EntityQueryResult<List<Object>> result = this.operations.searchVectors((Class<Object>) this.domainClass,
				builder.build());
		return this.execution.execute(result);
	}

	protected abstract int topK();

	protected abstract String indexName();

	protected abstract void applySearchCondition(DynamoDbVectorSearchRequest.Builder builder, Object[] parameters);

	private static int vectorParameterIndex(DynamoDbQueryMethod queryMethod) {
		Parameter[] parameters = queryMethod.getMethod().getParameters();
		for (int i = 0; i < parameters.length; i++) {
			if (parameters[i].getType() == float[].class && !parameters[i].isAnnotationPresent(Filter.class)) {
				return i;
			}
		}
		throw new IllegalStateException(
				"A vector search method must declare a float[] query-vector parameter: " + queryMethod.getMethod());
	}

	private static DynamoDbQueryExecution execution(DynamoDbQueryMethod queryMethod) {
		if (queryMethod.isCollectionQuery()) {
			return new DynamoDbQueryExecution.CollectionExecution();
		}
		if (queryMethod.isScrollQuery()) {
			return new DynamoDbQueryExecution.WindowExecution();
		}
		return new DynamoDbQueryExecution.SingleEntityExecution(true);
	}
}
