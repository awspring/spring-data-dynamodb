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

import io.awspring.spring.data.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.spring.data.dynamodb.request.DynamoDbConditionRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbPageRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbQueryRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbScanRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbUpdateExpressionRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbUpdateExpressionRequestInterface;
import io.awspring.spring.data.dynamodb.request.IndexQueryBuilder;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Main API for DynamoDB persistence, reads, queries, scans, and updates.
 *
 * @author Matej Nedic
 * @since 1.0.0
 */
public interface DynamoDbOperations {

	/** Saves an entity with {@code PutItem}. */
	<T> EntityWriteResult<T> save(T entity);

	/** Saves an entity with a conditional {@code PutItem}. */
	<T> EntityWriteResult<T> save(T entity, DynamoDbConditionRequest dynamoDBConditionRequest);

	/** Inserts an entity and fails if its key already exists. */
	<T> EntityWriteResult<T> insert(T entity);

	/** Saves entities with chunked {@code BatchWriteItem} requests. */
	<T> Iterable<T> saveAll(Iterable<? extends T> entities);

	/** Deletes the supplied entity. */
	void delete(Object entity);

	/** Deletes an item by partition and optional sort key. */
	<T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey);

	/** Deletes an item by key when the condition succeeds. */
	<T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey,
			DynamoDbConditionRequest dynamoDBConditionRequest);

	/** @return the converter used by these operations */
	DynamoDbConverter getConverter();

	/** Executes one DynamoDB query page. */
	<T> EntityQueryResult<List<T>> query(Class<T> entityClass, DynamoDbQueryRequest queryRequest,
			DynamoDbPageRequest dynamoDBPageRequest);

	/** Executes a PartiQL statement with positional values. */
	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass,
			List<Object> values);

	/** Executes a PartiQL statement without positional values. */
	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass);

	/** Executes a PartiQL statement with positional values and explicit read consistency. */
	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass,
			List<Object> values, Boolean consistentRead);

	/** Finds an item by its partition key. */
	@Nullable
	<T> T findById(Object id, Class<T> entityClass);

	/** Finds an item by its partition key with explicit read consistency. */
	@Nullable
	<T> T findById(Object id, Class<T> entityClass, Boolean consistentRead);

	/** Finds an item by its partition and optional sort key. */
	@Nullable
	<T> T findById(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass);

	/** Finds an item by both keys with explicit read consistency. */
	@Nullable
	<T> T findById(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass, Boolean consistentRead);

	/** Checks whether an item exists without converting it. */
	<T> boolean existsById(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass);

	/** Updates an item from the supplied entity state. */
	<T> EntityWriteResult<T> update(T entity);

	/** Updates an item with an explicit update request. */
	<T> EntityWriteResult<T> update(Object partitionKey, @Nullable Object sortKey,
			DynamoDbUpdateExpressionRequest dynamoDBUpdateExpressionRequest, Class<T> entityClass);

	/** Updates an item with a fluent update-expression callback. */
	<T> EntityWriteResult<T> update(Object partitionKey, @Nullable Object sortKey,
			DynamoDbUpdateExpressionRequestInterface builderFunction, Class<T> entityClass);

	/** Executes one DynamoDB scan page. */
	<T> EntityQueryResult<List<T>> scan(Class<T> entityClass, DynamoDbScanRequest scanRequest);

	/** Counts every item visible through the entity mapping. */
	<T> long count(Class<T> entityClass);

	/** Counts items matching a scan request. */
	<T> long count(Class<T> entityClass, DynamoDbScanRequest scanRequest);

	/** Counts items matching a query request. */
	<T> long count(Class<T> entityClass, DynamoDbQueryRequest queryRequest);

	/** Checks whether a scan has at least one match. */
	<T> boolean exists(Class<T> entityClass, DynamoDbScanRequest scanRequest);

	/** Checks whether a query has at least one match. */
	<T> boolean exists(Class<T> entityClass, DynamoDbQueryRequest queryRequest);

	/** Reads all items visible through the entity mapping. */
	<T> List<T> findAll(Class<T> entityClass);

	/** @return the resolved physical table name for the entity */
	String getTableName(Class<?> entityClass);

	/**
	 * Queries and folds one DynamoDB page into an item-collection view.
	 * @return the folded page, count, and optional continuation cursor
	 */
	<A> EntityQueryResult<A> queryItemCollection(Class<A> viewClass, DynamoDbQueryRequest dynamoDbRequest,
			DynamoDbPageRequest dynamoDBPageRequest);

	/** Starts a typed query against the named index. */
	<T> IndexQueryBuilder<T> query(Class<T> entityClass, String indexName);
}
