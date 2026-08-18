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
package io.awspring.cloud.dynamodb.core;

import io.awspring.cloud.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.cloud.dynamodb.request.DynamoDbConditionRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbPageRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbQueryRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbScanRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbUpdateExpressionRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbUpdateExpressionRequestInterface;
import io.awspring.cloud.dynamodb.request.IndexQueryBuilder;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public interface DynamoDbOperations {
	<T> EntityWriteResult<T> save(T entity);

	<T> EntityWriteResult<T> save(T entity, DynamoDbConditionRequest dynamoDBConditionRequest);

	<T> EntityWriteResult<T> insert(T entity);

	<T> Iterable<T> saveAll(Iterable<? extends T> entities);

	void delete(Object entity);

	<T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey);

	<T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey,
			DynamoDbConditionRequest dynamoDBConditionRequest);

	DynamoDbConverter getConverter();

	<T> EntityQueryResult<List<T>> query(Class<T> entityClass, DynamoDbQueryRequest queryRequest,
			DynamoDbPageRequest dynamoDBPageRequest);

	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass,
			List<Object> values);

	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass);

	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass,
			List<Object> values, Boolean consistentRead);

	@Nullable
	<T> T findById(Object id, Class<T> entityClass);

	@Nullable
	<T> T findById(Object id, Class<T> entityClass, Boolean consistentRead);

	@Nullable
	<T> T findById(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass);

	@Nullable
	<T> T findById(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass, Boolean consistentRead);

	<T> boolean existsById(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass);

	<T> EntityWriteResult<T> update(T entity);

	<T> EntityWriteResult<T> update(Object partitionKey, @Nullable Object sortKey,
			DynamoDbUpdateExpressionRequest dynamoDBUpdateExpressionRequest, Class<T> entityClass);

	<T> EntityWriteResult<T> update(Object partitionKey, @Nullable Object sortKey,
			DynamoDbUpdateExpressionRequestInterface builderFunction, Class<T> entityClass);

	<T> EntityQueryResult<List<T>> scan(Class<T> entityClass, DynamoDbScanRequest scanRequest);

	<T> long count(Class<T> entityClass);

	<T> long count(Class<T> entityClass, DynamoDbScanRequest scanRequest);

	<T> long count(Class<T> entityClass, DynamoDbQueryRequest queryRequest);

	<T> boolean exists(Class<T> entityClass, DynamoDbScanRequest scanRequest);

	<T> boolean exists(Class<T> entityClass, DynamoDbQueryRequest queryRequest);

	<T> List<T> findAll(Class<T> entityClass);

	EntityQueryResult<List<Object>> queryPolymorphic(String tableName, DynamoDbQueryRequest queryRequest,
			DynamoDbPageRequest dynamoDBPageRequest);

	EntityQueryResult<List<Object>> scanPolymorphic(String tableName, DynamoDbScanRequest scanRequest);

	String getTableName(Class<?> entityClass);

	<A> EntityQueryResult<A> queryAggregate(Class<A> aggregateClass, DynamoDbQueryRequest dynamoDbRequest,
			DynamoDbPageRequest dynamoDBPageRequest);

	<T> IndexQueryBuilder<T> query(Class<T> entityClass, String indexName);
}
