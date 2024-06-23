package io.awspring.cloud.v3.dynamodb.core;

import io.awspring.cloud.v3.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.cloud.v3.dynamodb.request.*;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;

public interface DynamoDbOperations {
	<T> EntityWriteResult<T> save(T entity);
	<T> EntityWriteResult<T> save(T entity, DynamoDBConditionRequest dynamoDBConditionRequest);

	<T> Iterable<T> saveAll(Iterable<T> entities, Class entityClass);

	void delete(Object entity);
	<T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey);
	<T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey, DynamoDBConditionRequest dynamoDBConditionRequest);

	DynamoDbConverter getConverter();

	<T> EntityQueryResult<List<T>> query(Class<T> entityClass, DynamoDBQueryRequest queryRequest, DynamoDBPageRequest dynamoDBPageRequest);

	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass, List<Object> values);
	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass);
	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass, List<Object> values, Boolean consistentRead);

	<T> T getEntityByKey(Object id, Class<T> entityClass);
	<T> T getEntityByKey(Object id, Class<T> entityClass, Boolean consistentRead);
	<T> T findEntityByKeys(Object partitionKey, @Nullable  Object sortKey, Class<T> entityClass);
	<T> T findEntityByKeys(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass, Boolean consistentRead);

	<T> EntityWriteResult<T> update(T entity);
	<T> EntityWriteResult<T> update(Object partitionKey, @Nullable Object sortKey, DynamoDBUpdateExpressionRequest dynamoDBUpdateExpressionRequest, Class<T> entityClass);
	<T> EntityQueryResult<List<T>> scan(Class<T> entityClass, DynamoDbScanRequest scanRequest);
}
