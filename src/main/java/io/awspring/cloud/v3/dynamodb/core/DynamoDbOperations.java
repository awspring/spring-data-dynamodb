package io.awspring.cloud.v3.dynamodb.core;

import io.awspring.cloud.v3.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.cloud.v3.dynamodb.request.*;
import org.springframework.lang.Nullable;

import java.util.List;

public interface DynamoDbOperations {
	<T> EntityWriteResult<T> save(T entity);
	<T> EntityWriteResult<T> save(T entity, DynamoDbConditionRequest dynamoDBConditionRequest);

	<T> EntityWriteResult<T> save(T entity, DynamoDbConditionRequestInterface builderFunction);

	<T> Iterable<T> saveAll(Iterable<T> entities, Class entityClass);

	void delete(Object entity);
	<T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey);
	<T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey, DynamoDbConditionRequest dynamoDBConditionRequest);

	<T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey, DynamoDbConditionRequestInterface builderFunction);

	DynamoDbConverter getConverter();

	<T> EntityQueryResult<List<T>> query(Class<T> entityClass, DynamoDbQueryRequest queryRequest, DynamoDbPageRequest dynamoDBPageRequest);

	<T> EntityQueryResult<List<T>> query(Class<T> entityClass, DynamoDbQueryRequestInterface builderFunction, DynamoDbPageRequest dynamoDBPageRequest);

	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass, List<Object> values);
	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass);
	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass, List<Object> values, Boolean consistentRead);

	<T> T getEntityByKey(Object id, Class<T> entityClass);
	<T> T getEntityByKey(Object id, Class<T> entityClass, Boolean consistentRead);
	<T> T findEntityByKeys(Object partitionKey, @Nullable  Object sortKey, Class<T> entityClass);
	<T> T findEntityByKeys(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass, Boolean consistentRead);

	<T> EntityWriteResult<T> update(T entity);
	<T> EntityWriteResult<T> update(Object partitionKey, @Nullable Object sortKey, DynamoDbUpdateExpressionRequest dynamoDBUpdateExpressionRequest, Class<T> entityClass);

	<T> EntityWriteResult<T> update(Object partitionKey, @Nullable Object sortKey, DynamoDbUpdateExpressionRequestInterface builderFunction, Class<T> entityClass);
	<T> EntityQueryResult<List<T>> scan(Class<T> entityClass, DynamoDbScanRequest scanRequest);

	<T> EntityQueryResult<List<T>> scan(Class<T> entityClass, DynamoDbScanRequestInterface builderFunction);
}
