package io.awspring.cloud.v3.dynamodb.core;

import io.awspring.cloud.v3.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbPersistenceEntity;
import io.awspring.cloud.v3.dynamodb.request.*;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.lang.Nullable;
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

	public StatementFactory(DynamoDbConverter dynamoDbConverter) {
		this.dynamoDbConverter = dynamoDbConverter;
	}


	public PutItemRequest insert(Object objectToInsert,
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
			builder.expressionAttributeValues(expressionAttributesToBuild);
		}


		return builder.build();
	}

	public PutRequest insertAll(Object objectToInsert,
						 DynamoDbPersistenceEntity<?> persistentEntity) {

		Assert.notNull(objectToInsert, "Object to insert must not be null");
		Assert.notNull(persistentEntity, "DynamoDbPersistenceEntity must not be null");


		Map<String, AttributeValue> object = new LinkedHashMap<>();
		dynamoDbConverter.write(objectToInsert, object, persistentEntity);

		return PutRequest.builder().item(object).build();
	}

	public DeleteItemRequest delete(Object objectToDelete, DynamoDbPersistenceEntity persistenceEntity, String tableName) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(objectToDelete, "Object to delete must not be null");
		Assert.notNull(persistenceEntity, "DynamoDbPersistenceEntity must not be null");

		Map<String, AttributeValue> keys = new LinkedHashMap<>();
		dynamoDbConverter.delete(objectToDelete, keys, persistenceEntity);

		return DeleteItemRequest.builder().tableName(tableName).key(keys).build();
	}

	public DeleteItemRequest delete(Object partitionKey, @Nullable Object sortKey, DynamoDbPersistenceEntity<?> requiredPersistentEntity, String tableName, DynamoDBConditionRequest dynamoDBConditionRequest) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(partitionKey, "Partition key must not be null");
		Assert.notNull(requiredPersistentEntity, "DynamoDbPersistenceEntity must not be null");

		Map<String, AttributeValue> keysToBeUsed = new LinkedHashMap<>(2);
		keysToBeUsed.put(requiredPersistentEntity.getIdProperty().getColumnName(), dynamoDbConverter.convertToDynamoDbType(partitionKey, requiredPersistentEntity));
		if (sortKey != null) {
			keysToBeUsed.put(requiredPersistentEntity.getSortKey().getColumnName(), dynamoDbConverter.convertToDynamoDbType(sortKey, requiredPersistentEntity));
		}
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
			deleteItemRequestBuilder.expressionAttributeValues(expressionAttributesToBuild);
		}


		return deleteItemRequestBuilder.build();
	}

	public GetItemRequest findByKey(Object key, String tableName, DynamoDbPersistenceEntity<?> entity, Boolean consistentRead) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(key, "Key must not be null");
		Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");
		Map<String, AttributeValue> keys = new LinkedHashMap<>();
		dynamoDbConverter.findByKey(key, keys, entity);
		return GetItemRequest.builder().tableName(tableName).consistentRead(consistentRead)
			.key(keys).build();
	}


	public ExecuteStatementRequest executeStatementRequest(String statement, String nextToken, List<Object> parameters,
													DynamoDbPersistenceEntity<?> entity, Boolean consistentRead) {
		Assert.notNull(statement, "Statement must not be null");
		Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");

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

	public GetItemRequest findByKeys(String partitionKey, String sortKey, String tableName, DynamoDbPersistenceEntity<?> entity, Boolean consistentRead) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(partitionKey, "Keys must not be null");
		Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");
		Map<String, AttributeValue> keys = new LinkedHashMap<>();

		dynamoDbConverter.findByKeys(partitionKey, sortKey, keys, entity);
		return GetItemRequest.builder().tableName(tableName).consistentRead(consistentRead)
			.key(keys).build();
	}

	public UpdateItemRequest update(Object objectToUpdate, String tableName, DynamoDbPersistenceEntity<?> entity) {
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
			queryRequestBuilder.exclusiveStartKey(exclusiveStartKeys);

			if (dynamoDBPageRequest.getLimit() != null) {
				queryRequestBuilder.limit(dynamoDBPageRequest.getLimit());
			}
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
		if (StringUtils.hasText(entity.getGlobalSecondaryIndex())) {
			queryRequestBuilder.indexName(entity.getGlobalSecondaryIndex());
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
			builder.expressionAttributeValues(expressionAttributesToBuild);
		}
		return builder.build();
	}

    public ScanRequest scan(String tableName, DynamoDbScanRequest request, DynamoDbPersistenceEntity<?> entity) {
		var builder = ScanRequest.builder();
		builder.consistentRead(request.isConsistentRead());
		builder.tableName(tableName);
		if (StringUtils.hasText(entity.getGlobalSecondaryIndex())) {
			builder.indexName(entity.getGlobalSecondaryIndex());
		}
		if (request.getIndexName() != null) {
			builder.indexName(request.getIndexName());
		}
		if (request.getLimit() != null) {
			builder.limit(request.getLimit());
		}
		if (request.getExpressionAttributeNames() != null) {
			builder.expressionAttributeNames(request.getExpressionAttributeNames());
		}
		if (request.getExpressionAttributeValues() != null) {
			Map<String, AttributeValue> expressionAttributesToBuild = new HashMap<>(request.getExpressionAttributeValues().size());
			request.getExpressionAttributeValues().forEach((k, v) -> expressionAttributesToBuild.put(k, dynamoDbConverter.convertToDynamoDbType(v, entity)));
			builder.expressionAttributeValues(expressionAttributesToBuild);
		}
		if (request.getFilterExpression() != null) {
			builder.filterExpression(request.getFilterExpression());
		}
		if (request.getExclusiveStartKey() != null) {
			Map<String, AttributeValue> exclusiveKey = new HashMap<>(request.getExclusiveStartKey().size());
			request.getExclusiveStartKey().forEach((k, v) -> exclusiveKey.put(k, dynamoDbConverter.convertToDynamoDbType(v, entity)));
			builder.exclusiveStartKey(exclusiveKey);
		}
		if (request.getSelect() != null) {
			builder.select(request.getSelect());
		}
		if (request.getProjectionExpression() != null ) {
			builder.projectionExpression(request.getProjectionExpression());
		}
		return builder.build();
    }
}
