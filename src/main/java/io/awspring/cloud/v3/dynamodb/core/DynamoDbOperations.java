package io.awspring.cloud.v3.dynamodb.core;

import io.awspring.cloud.v3.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.cloud.v3.dynamodb.request.DynamoDBConditionRequest;
import io.awspring.cloud.v3.dynamodb.request.DynamoDBPageRequest;
import io.awspring.cloud.v3.dynamodb.request.DynamoDBQueryRequest;
import io.awspring.cloud.v3.dynamodb.request.DynamoDBUpdateExpressionRequest;

import java.util.List;
import java.util.Map;

public interface DynamoDbOperations {
	<T> EntityWriteResult<T> save(T entity);
	<T> EntityWriteResult<T> save(T entity, DynamoDBConditionRequest dynamoDBConditionRequest);

	<T> Iterable<T> saveAll(Iterable<T> entities, Class entityClass);

	void delete(Object entity);
	<T> void delete(Class<T> entityClass, Map<String, Object> keys);
	<T> void delete(Class<T> entityClass,Map<String, Object> keys, DynamoDBConditionRequest dynamoDBConditionRequest);

	DynamoDbConverter getConverter();

	<T> EntityQueryResult<List<T>> query(Class<T> entityClass, DynamoDBQueryRequest queryRequest, DynamoDBPageRequest dynamoDBPageRequest);

	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass, List<Object> values);
	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass);
	<T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass, List<Object> values, Boolean consistentRead);

	<T> T getEntityByKey(Object id, Class<T> entityClass);
	<T> T getEntityByKey(Object id, Class<T> entityClass, Boolean consistentRead);
	<T> T findEntityByKeys(String partitionKey, String sortKey, Class<T> entityClass);
	<T> T findEntityByKeys(String partitionKey, String sortKey, Class<T> entityClass, Boolean consistentRead);

	<T> EntityWriteResult<T> update(T entity);
	<T> EntityWriteResult<T> update(Map<String, Object> keys, DynamoDBUpdateExpressionRequest dynamoDBUpdateExpressionRequest, Class<T> entityClass);
}
