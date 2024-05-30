package io.awspring.cloud.v3.dynamodb.core;

import io.awspring.cloud.v3.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbPersistenceEntity;
import io.awspring.cloud.v3.dynamodb.request.DynamoDBConditionRequest;
import io.awspring.cloud.v3.dynamodb.request.DynamoDBPageRequest;
import io.awspring.cloud.v3.dynamodb.request.DynamoDBQueryRequest;
import io.awspring.cloud.v3.dynamodb.request.DynamoDBUpdateExpressionRequest;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class StatementFactory {


	private final DynamoDbConverter dynamoDbConverter;


	private final ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();

	public StatementFactory(DynamoDbConverter dynamoDbConverter) {
		this.dynamoDbConverter = dynamoDbConverter;
	}


	public PutItemRequest insert(Object objectToInsert) {

		Assert.notNull(objectToInsert, "Object to builder must not be null");

		DynamoDbPersistenceEntity<?> persistentEntity = dynamoDbConverter.getMappingContext()
			.getRequiredPersistentEntity(objectToInsert.getClass());

		return insert(objectToInsert, persistentEntity, persistentEntity.getTableName(), new DynamoDBConditionRequest());
	}

	PutItemRequest insert(Object objectToInsert,
						  DynamoDbPersistenceEntity<?> persistentEntity, String tableName, DynamoDBConditionRequest dynamoDBConditionRequest) {

		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(objectToInsert, "Object to insert must not be null");
		Assert.notNull(persistentEntity, "DynamoDbPersistenceEntity must not be null");


		Map<String, AttributeValue> object = new LinkedHashMap<>();
		dynamoDbConverter.write(objectToInsert, object, persistentEntity);
		PutItemRequest.Builder builder = PutItemRequest.builder().item(object).tableName(tableName);
		if (dynamoDBConditionRequest.getConditionExpression() != null) {
			builder.conditionExpression(dynamoDBConditionRequest.getConditionExpression());
		}
		if (dynamoDBConditionRequest.getExpressionAttributeNames() != null) {
			builder.expressionAttributeNames(dynamoDBConditionRequest.getExpressionAttributeNames());
		}
		if (dynamoDBConditionRequest.getExpressionAttributeValues() != null) {
			Map<String, AttributeValue> expressionAttributesToBuild = new HashMap<>(dynamoDBConditionRequest.getExpressionAttributeValues().size());
			dynamoDBConditionRequest.getExpressionAttributeValues().forEach((k, v) -> expressionAttributesToBuild.put(k, dynamoDbConverter.convertToDynamoDbType(v, persistentEntity)));
		}


		return builder.build();
	}

	PutRequest insertAll(Object objectToInsert,
						 DynamoDbPersistenceEntity<?> persistentEntity) {

		Assert.notNull(objectToInsert, "Object to insert must not be null");
		Assert.notNull(persistentEntity, "DynamoDbPersistenceEntity must not be null");


		Map<String, AttributeValue> object = new LinkedHashMap<>();
		dynamoDbConverter.write(objectToInsert, object, persistentEntity);

		return PutRequest.builder().item(object).build();
	}

	DeleteItemRequest delete(Object objectToDelete, DynamoDbPersistenceEntity persistenceEntity, String tableName) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(objectToDelete, "Object to delete must not be null");
		Assert.notNull(persistenceEntity, "DynamoDbPersistenceEntity must not be null");

		Map<String, AttributeValue> keys = new LinkedHashMap<>();
		dynamoDbConverter.delete(objectToDelete, keys, persistenceEntity);

		return DeleteItemRequest.builder().tableName(tableName).key(keys).build();
	}

	DeleteItemRequest delete(Map<String, Object> keys, DynamoDbPersistenceEntity<?> requiredPersistentEntity, String tableName, DynamoDBConditionRequest dynamoDBConditionRequest) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(keys, "Keys to delete must not be null");
		Assert.notNull(requiredPersistentEntity, "DynamoDbPersistenceEntity must not be null");

		Map<String, AttributeValue> keysToBeUsed = new LinkedHashMap<>(keys.size());
		keys.forEach((k, v) -> {
			keysToBeUsed.put(k, dynamoDbConverter.convertToDynamoDbType(v, requiredPersistentEntity));
		});
		DeleteItemRequest.Builder deleteItemRequestBuilder = DeleteItemRequest.builder().tableName(tableName).key(keysToBeUsed);
		if (dynamoDBConditionRequest.getConditionExpression() != null) {
			deleteItemRequestBuilder.conditionExpression(dynamoDBConditionRequest.getConditionExpression());
		}
		if (dynamoDBConditionRequest.getExpressionAttributeNames() != null) {
			deleteItemRequestBuilder.expressionAttributeNames(dynamoDBConditionRequest.getExpressionAttributeNames());
		}
		if (dynamoDBConditionRequest.getExpressionAttributeValues() != null) {
			Map<String, AttributeValue> expressionAttributesToBuild = new HashMap<>(dynamoDBConditionRequest.getExpressionAttributeValues().size());
			dynamoDBConditionRequest.getExpressionAttributeValues().forEach((k, v) -> expressionAttributesToBuild.put(k, dynamoDbConverter.convertToDynamoDbType(v, requiredPersistentEntity)));
		}


		return deleteItemRequestBuilder.build();
	}

	GetItemRequest findByKey(Object key, String tableName, DynamoDbPersistenceEntity<?> entity, Boolean consistentRead) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(key, "Key must not be null");
		Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");
		Map<String, AttributeValue> keys = new LinkedHashMap<>();
		dynamoDbConverter.findByKey(key, keys, entity);
		return GetItemRequest.builder().tableName(tableName).consistentRead(consistentRead)
			.key(keys).build();
	}


	ExecuteStatementRequest executeStatementRequest(String statement, String nextToken, List<Object> parameters,
													DynamoDbPersistenceEntity<?> entity, Boolean consistentRead) {
		Assert.notNull(statement, "Statement must not be null");
		Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");
		Map<String, AttributeValue> keys = new LinkedHashMap<>();

		ExecuteStatementRequest.Builder builder = ExecuteStatementRequest.builder().statement(statement);
		if (nextToken != null) {
			builder.nextToken(nextToken);
		}
		if (parameters != null) {
			List<AttributeValue> attributeValues = parameters.stream()
				.map(par -> dynamoDbConverter.convertToDynamoDbType(par, entity)).collect(Collectors.toList());
			builder.parameters(attributeValues);
		}
		if (consistentRead != null) {
			builder.consistentRead(consistentRead);
		}
		return builder.build();
	}

	GetItemRequest findByKeys(String partitionKey, String sortKey, String tableName, DynamoDbPersistenceEntity<?> entity, Boolean consistentRead) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(partitionKey, "Keys must not be null");
		Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");
		Map<String, AttributeValue> keys = new LinkedHashMap<>();

		dynamoDbConverter.findByKeys(partitionKey, sortKey, keys, entity);
		return GetItemRequest.builder().tableName(tableName).consistentRead(consistentRead)
			.key(keys).build();
	}

	UpdateItemRequest update(Object objectToUpdate, String tableName, DynamoDbPersistenceEntity<?> entity) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(objectToUpdate, "ObjectToUpdate must not be null");
		Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");


		Map<String, AttributeValue> keys = new HashMap<>();
		Map<String, AttributeValueUpdate> values = new HashMap<>();
		dynamoDbConverter.update(objectToUpdate, keys, entity, values);

		return UpdateItemRequest.builder().tableName(tableName).attributeUpdates(values).key(keys).build();
	}

	public QueryRequest query(String tableName, DynamoDbPersistenceEntity entity, DynamoDBQueryRequest qr, DynamoDBPageRequest dynamoDBPageRequest) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(qr, "DynamoDBQueryRequest must not be null");
		Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");
		QueryRequest.Builder queryRequestBuilder = QueryRequest.builder().select(Select.ALL_ATTRIBUTES);
		if (dynamoDBPageRequest != null) {
			Map<String, AttributeValue> exclusiveStartKeys = new HashMap<>(dynamoDBPageRequest.getLastEvaluatedKey().size());
			dynamoDBPageRequest.getLastEvaluatedKey().forEach((k, v) -> {
				exclusiveStartKeys.put(k, dynamoDbConverter.convertToDynamoDbType(v, entity));
			});
			queryRequestBuilder.limit(dynamoDBPageRequest.getLimit()).exclusiveStartKey(exclusiveStartKeys);
		}

		queryRequestBuilder.consistentRead(qr.getConsistentRead()).scanIndexForward(qr.getScanIndexForward());
		if (qr.getExpressionAttributeNames() != null) {
			queryRequestBuilder.expressionAttributeNames(qr.getExpressionAttributeNames());
		}
		if (qr.getExpressionAttributeValues() != null) {
			Map<String, AttributeValue> mapOfExpressionAttributeValues = new HashMap<>(qr.getExpressionAttributeValues().size());
			qr.getExpressionAttributeValues().forEach((k,v) -> {
				mapOfExpressionAttributeValues.put(k, dynamoDbConverter.convertToDynamoDbType(v, entity));
			});
			queryRequestBuilder.expressionAttributeValues(mapOfExpressionAttributeValues);
		}
		if (StringUtils.hasLength(qr.getIndexName())) {
			queryRequestBuilder.indexName(qr.getIndexName());
		}
		if (StringUtils.hasLength(qr.getKeyConditionExpression())) {
			queryRequestBuilder.keyConditionExpression(qr.getKeyConditionExpression());
		}
		if (StringUtils.hasLength(qr.getFilterExpression())) {
			queryRequestBuilder.filterExpression(qr.getFilterExpression());
		}
		return queryRequestBuilder.tableName(tableName).build();
	}


	public UpdateItemRequest update(Map<String, Object> keys, DynamoDBUpdateExpressionRequest request, String tableName, DynamoDbPersistenceEntity entity) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(keys, "Keys must not be null");
		Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");
		Assert.notNull(request.getUpdateExpression(), "UpdateExpression must not be null");

		Map<String, AttributeValue> keysToBeUsed = new HashMap<>(keys.size());
		keys.forEach((k, v) -> {
			keysToBeUsed.put(k, dynamoDbConverter.convertToDynamoDbType(v, entity));
		});

		UpdateItemRequest.Builder builder = UpdateItemRequest.builder().tableName(tableName).key(keysToBeUsed).updateExpression(request.getUpdateExpression()).returnValues(ReturnValue.ALL_NEW);
		if (request.getConditionExpression() != null) {
			builder.conditionExpression(request.getConditionExpression());
		}
		if (request.getExpressionAttributeNames() != null) {
			builder.expressionAttributeNames(request.getExpressionAttributeNames());
		}
		if (request.getExpressionAttributeValues() != null) {
			Map<String, AttributeValue> expressionAttributesToBuild = new HashMap<>(request.getExpressionAttributeValues().size());
			request.getExpressionAttributeValues().forEach((k, v) -> expressionAttributesToBuild.put(k, dynamoDbConverter.convertToDynamoDbType(v, entity)));
		}
		return builder.build();
	}
}
