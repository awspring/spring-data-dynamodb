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
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.spring.data.dynamodb.request.DynamoDbConditionRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbPageRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbQueryRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbScanRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbUpdateExpressionRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.dynamodb.model.*;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class StatementFactory {

	private final DynamoDbConverter dynamoDbConverter;

	public StatementFactory(DynamoDbConverter dynamoDbConverter) {
		this.dynamoDbConverter = dynamoDbConverter;
	}

	private static void rejectStronglyConsistentIndexRead(@Nullable String indexName,
			@Nullable Boolean consistentRead) {
		if (StringUtils.hasText(indexName) && Boolean.TRUE.equals(consistentRead)) {
			throw new InvalidDataAccessApiUsageException("DynamoDB global secondary indexes support only eventually "
					+ "consistent reads; consistentRead=true cannot be used with index '" + indexName + "'.");
		}
	}

	private static void rejectIfSecondaryIndexView(DynamoDbPersistentEntity<?> entity, String operation) {
		if (entity.isSecondaryIndexView()) {
			throw new IllegalStateException(
					String.format("%s is a @SecondaryIndex view (index '%s') and does not support %s.",
							entity.getType().getName(), entity.getIndexName(), operation));
		}
	}

	public PutItemRequest insert(Object objectToInsert, DynamoDbPersistentEntity<?> persistentEntity, String tableName,
			DynamoDbConditionRequest dynamoDBConditionRequest) {

		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(objectToInsert, "Object to insert must not be null");
		Assert.notNull(persistentEntity, "DynamoDbPersistentEntity must not be null");
		rejectIfSecondaryIndexView(persistentEntity, "PutItem (save/insert)");

		Map<String, AttributeValue> object = new LinkedHashMap<>();
		dynamoDbConverter.write(objectToInsert, object, persistentEntity);
		PutItemRequest.Builder builder = PutItemRequest.builder().item(object).tableName(tableName);
		if (dynamoDBConditionRequest.getConditionExpression() != null) {
			builder.conditionExpression(dynamoDBConditionRequest.getConditionExpression());
		}
		Map<String, String> conditionNames = dynamoDBConditionRequest.getExpressionAttributeNames();
		if (conditionNames != null && !conditionNames.isEmpty()) {
			builder.expressionAttributeNames(conditionNames);
		}
		Map<String, Object> conditionValues = dynamoDBConditionRequest.getExpressionAttributeValues();
		if (conditionValues != null && !conditionValues.isEmpty()) {
			Map<String, AttributeValue> expressionAttributesToBuild = new HashMap<>(conditionValues.size());
			conditionValues.forEach((k, v) -> expressionAttributesToBuild.put(k,
					dynamoDbConverter.convertToDynamoDbType(v, persistentEntity)));
			builder.expressionAttributeValues(expressionAttributesToBuild);
		}

		return builder.build();
	}

	public PutRequest insertAll(Object objectToInsert, DynamoDbPersistentEntity<?> persistentEntity) {

		Assert.notNull(objectToInsert, "Object to insert must not be null");
		Assert.notNull(persistentEntity, "DynamoDbPersistentEntity must not be null");
		rejectIfSecondaryIndexView(persistentEntity, "PutItem (saveAll)");

		Map<String, AttributeValue> object = new LinkedHashMap<>();
		dynamoDbConverter.write(objectToInsert, object, persistentEntity);

		return PutRequest.builder().item(object).build();
	}

	public WriteRequest writeRequestForBatchPut(Object objectToInsert, DynamoDbPersistentEntity<?> persistentEntity) {
		return WriteRequest.builder().putRequest(insertAll(objectToInsert, persistentEntity)).build();
	}

	public BatchWriteItemRequest batchWrite(Map<String, List<WriteRequest>> requestItems) {
		Assert.notNull(requestItems, "RequestItems must not be null");
		return BatchWriteItemRequest.builder().requestItems(requestItems).build();
	}

	public DeleteItemRequest delete(Object objectToDelete, DynamoDbPersistentEntity<?> persistenceEntity,
			String tableName) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(objectToDelete, "Object to delete must not be null");
		Assert.notNull(persistenceEntity, "DynamoDbPersistentEntity must not be null");
		rejectIfSecondaryIndexView(persistenceEntity, "DeleteItem");

		Map<String, AttributeValue> keys = new LinkedHashMap<>();
		dynamoDbConverter.writeKeyFromEntity(objectToDelete, keys, persistenceEntity);

		return DeleteItemRequest.builder().tableName(tableName).key(keys).build();
	}

	public DeleteItemRequest delete(Object partitionKey, @Nullable Object sortKey,
			DynamoDbPersistentEntity<?> requiredPersistentEntity, String tableName,
			DynamoDbConditionRequest dynamoDBConditionRequest) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(partitionKey, "Partition key must not be null");
		Assert.notNull(requiredPersistentEntity, "DynamoDbPersistentEntity must not be null");
		rejectIfSecondaryIndexView(requiredPersistentEntity, "DeleteItem");

		Map<String, AttributeValue> keysToBeUsed = new LinkedHashMap<>(2);
		dynamoDbConverter.writeKey(partitionKey, sortKey, keysToBeUsed, requiredPersistentEntity);
		DeleteItemRequest.Builder deleteItemRequestBuilder = DeleteItemRequest.builder().tableName(tableName)
				.key(keysToBeUsed);
		if (dynamoDBConditionRequest.getConditionExpression() != null) {
			deleteItemRequestBuilder.conditionExpression(dynamoDBConditionRequest.getConditionExpression());
		}
		if (dynamoDBConditionRequest.getExpressionAttributeNames() != null) {
			deleteItemRequestBuilder.expressionAttributeNames(dynamoDBConditionRequest.getExpressionAttributeNames());
		}
		if (dynamoDBConditionRequest.getExpressionAttributeValues() != null) {
			Map<String, AttributeValue> expressionAttributesToBuild = new HashMap<>(
					dynamoDBConditionRequest.getExpressionAttributeValues().size());
			dynamoDBConditionRequest.getExpressionAttributeValues().forEach((k, v) -> expressionAttributesToBuild.put(k,
					dynamoDbConverter.convertToDynamoDbType(v, requiredPersistentEntity)));
			deleteItemRequestBuilder.expressionAttributeValues(expressionAttributesToBuild);
		}

		return deleteItemRequestBuilder.build();
	}

	public GetItemRequest findByKey(Object key, String tableName, DynamoDbPersistentEntity<?> entity,
			Boolean consistentRead) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(key, "Key must not be null");
		Assert.notNull(entity, "DynamoDbPersistentEntity must not be null");
		rejectIfSecondaryIndexView(entity, "GetItem (findById)");
		Map<String, AttributeValue> keys = new LinkedHashMap<>();
		dynamoDbConverter.writeKey(key, keys, entity);
		return GetItemRequest.builder().tableName(tableName).consistentRead(consistentRead).key(keys).build();
	}

	public ExecuteStatementRequest executeStatementRequest(String statement, String nextToken, List<Object> parameters,
			DynamoDbPersistentEntity<?> entity, Boolean consistentRead) {
		Assert.notNull(statement, "Statement must not be null");
		Assert.notNull(entity, "DynamoDbPersistentEntity must not be null");

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

	public GetItemRequest findByKeys(Object partitionKey, Object sortKey, String tableName,
			DynamoDbPersistentEntity<?> entity, Boolean consistentRead) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(partitionKey, "Keys must not be null");
		Assert.notNull(entity, "DynamoDbPersistentEntity must not be null");
		rejectIfSecondaryIndexView(entity, "GetItem (findById)");
		Map<String, AttributeValue> keys = new LinkedHashMap<>();

		dynamoDbConverter.writeKey(partitionKey, sortKey, keys, entity);
		return GetItemRequest.builder().tableName(tableName).consistentRead(consistentRead).key(keys).build();
	}

	public GetItemRequest existsByKey(Object partitionKey, @Nullable Object sortKey, String tableName,
			DynamoDbPersistentEntity<?> entity) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(partitionKey, "Partition key must not be null");
		Assert.notNull(entity, "DynamoDbPersistentEntity must not be null");
		rejectIfSecondaryIndexView(entity, "GetItem (existsById)");

		Map<String, AttributeValue> keys = new LinkedHashMap<>();
		if (sortKey != null) {
			dynamoDbConverter.writeKey(partitionKey, sortKey, keys, entity);
		}
		else {
			dynamoDbConverter.writeKey(partitionKey, keys, entity);
		}

		String partitionColumn = entity.getIdProperty().getColumnName();
		return GetItemRequest.builder().tableName(tableName).key(keys).consistentRead(Boolean.FALSE)
				.projectionExpression("#__pk").expressionAttributeNames(Map.of("#__pk", partitionColumn)).build();
	}

	public UpdateItemRequest update(Object objectToUpdate, String tableName, DynamoDbPersistentEntity<?> entity,
			Number previousVersion) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(objectToUpdate, "ObjectToUpdate must not be null");
		Assert.notNull(entity, "DynamoDbPersistentEntity must not be null");
		rejectIfSecondaryIndexView(entity, "UpdateItem");

		Map<String, AttributeValue> keys = new HashMap<>();
		Map<String, AttributeValueUpdate> values = new HashMap<>();
		dynamoDbConverter.update(objectToUpdate, keys, entity, values);

		return buildUpdateItemRequest(tableName, keys, values, entity, previousVersion);
	}

	private UpdateItemRequest buildUpdateItemRequest(String tableName, Map<String, AttributeValue> keys,
			Map<String, AttributeValueUpdate> values, DynamoDbPersistentEntity<?> entity, Number previousVersion) {

		if (values.isEmpty()) {
			return UpdateItemRequest.builder().tableName(tableName).key(keys).build();
		}

		StringBuilder updateExpression = new StringBuilder("SET ");
		Map<String, String> expressionAttributeNames = new HashMap<>(values.size());
		Map<String, AttributeValue> expressionAttributeValues = new HashMap<>(values.size());

		int i = 0;
		for (Map.Entry<String, AttributeValueUpdate> entry : values.entrySet()) {
			String namePlaceholder = "#u" + i;
			String valuePlaceholder = ":u" + i;
			if (i > 0) {
				updateExpression.append(", ");
			}
			updateExpression.append(namePlaceholder).append(" = ").append(valuePlaceholder);
			expressionAttributeNames.put(namePlaceholder, entry.getKey());
			expressionAttributeValues.put(valuePlaceholder, entry.getValue().value());
			i++;
		}

		UpdateItemRequest.Builder builder = UpdateItemRequest.builder().tableName(tableName).key(keys)
				.updateExpression(updateExpression.toString());

		if (previousVersion != null && entity.hasVersionProperty()) {
			String versionColumnName = entity.getRequiredVersionProperty().getColumnName();
			expressionAttributeNames.put("#__version", versionColumnName);
			expressionAttributeValues.put(":__prevVersion",
					dynamoDbConverter.convertToDynamoDbType(previousVersion, entity));
			builder.conditionExpression("#__version = :__prevVersion");
		}

		builder.expressionAttributeNames(expressionAttributeNames).expressionAttributeValues(expressionAttributeValues);

		return builder.build();
	}

	public QueryRequest query(String tableName, DynamoDbPersistentEntity<?> entity, DynamoDbQueryRequest qr,
			DynamoDbPageRequest dynamoDBPageRequest) {
		return query(tableName, entity, qr, dynamoDBPageRequest, qr.getIndexName());
	}

	public QueryRequest query(String tableName, DynamoDbPersistentEntity<?> entity, DynamoDbQueryRequest qr,
			DynamoDbPageRequest dynamoDBPageRequest, String indexName) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(qr, "DynamoDBQueryRequest must not be null");
		Assert.notNull(entity, "DynamoDbPersistentEntity must not be null");
		if (StringUtils.hasText(qr.getIndexName())) {
			indexName = qr.getIndexName();
		}
		rejectStronglyConsistentIndexRead(indexName, qr.getConsistentRead());

		QueryRequest.Builder queryRequestBuilder = QueryRequest.builder().select(Select.ALL_ATTRIBUTES);
		if (dynamoDBPageRequest != null) {
			if (dynamoDBPageRequest.getLastEvaluatedKey() != null
					&& !dynamoDBPageRequest.getLastEvaluatedKey().isEmpty()) {
				Map<String, AttributeValue> exclusiveStartKeys = new HashMap<>(
						dynamoDBPageRequest.getLastEvaluatedKey().size());
				dynamoDBPageRequest.getLastEvaluatedKey().forEach((k, v) -> {
					exclusiveStartKeys.put(k, dynamoDbConverter.convertToDynamoDbType(v, entity));
				});
				queryRequestBuilder.exclusiveStartKey(exclusiveStartKeys);
			}
			if (dynamoDBPageRequest.getLimit() != null) {
				queryRequestBuilder.limit(dynamoDBPageRequest.getLimit());
			}
		}

		queryRequestBuilder.consistentRead(qr.getConsistentRead()).scanIndexForward(qr.getScanIndexForward());
		if (qr.getExpressionAttributeNames() != null) {
			queryRequestBuilder.expressionAttributeNames(qr.getExpressionAttributeNames());
		}
		if (qr.getExpressionAttributeValues() != null) {
			Map<String, AttributeValue> mapOfExpressionAttributeValues = new HashMap<>(
					qr.getExpressionAttributeValues().size());
			qr.getExpressionAttributeValues().forEach((k, v) -> {
				mapOfExpressionAttributeValues.put(k, dynamoDbConverter.convertToDynamoDbType(v, entity));
			});
			queryRequestBuilder.expressionAttributeValues(mapOfExpressionAttributeValues);
		}
		if (StringUtils.hasLength(indexName)) {
			queryRequestBuilder.indexName(indexName);
		}
		if (StringUtils.hasLength(qr.getKeyConditionExpression())) {
			queryRequestBuilder.keyConditionExpression(qr.getKeyConditionExpression());
		}
		if (StringUtils.hasLength(qr.getFilterExpression())) {
			queryRequestBuilder.filterExpression(qr.getFilterExpression());
		}
		return queryRequestBuilder.tableName(tableName).build();
	}

	public UpdateItemRequest update(Object partitionKey, @Nullable Object sortKey,
			DynamoDbUpdateExpressionRequest request, String tableName, DynamoDbPersistentEntity<?> entity) {
		Assert.notNull(tableName, "TableName must not be null");
		Assert.notNull(partitionKey, "Partition Key must not be null");
		Assert.notNull(entity, "DynamoDbPersistentEntity must not be null");
		Assert.notNull(request.getUpdateExpression(), "UpdateExpression must not be null");
		rejectIfSecondaryIndexView(entity, "UpdateItem");

		Map<String, AttributeValue> keysToBeUsed = new HashMap<>(2);
		dynamoDbConverter.writeKey(partitionKey, sortKey, keysToBeUsed, entity);

		UpdateItemRequest.Builder builder = UpdateItemRequest.builder().tableName(tableName).key(keysToBeUsed)
				.updateExpression(request.getUpdateExpression()).returnValues(ReturnValue.ALL_NEW);
		if (request.getConditionExpression() != null) {
			builder.conditionExpression(request.getConditionExpression());
		}
		if (request.getExpressionAttributeNames() != null) {
			builder.expressionAttributeNames(request.getExpressionAttributeNames());
		}
		if (request.getExpressionAttributeValues() != null) {
			Map<String, AttributeValue> expressionAttributesToBuild = new HashMap<>(
					request.getExpressionAttributeValues().size());
			request.getExpressionAttributeValues().forEach(
					(k, v) -> expressionAttributesToBuild.put(k, dynamoDbConverter.convertToDynamoDbType(v, entity)));
			builder.expressionAttributeValues(expressionAttributesToBuild);
		}
		return builder.build();
	}

	public ScanRequest scan(String tableName, DynamoDbScanRequest request, DynamoDbPersistentEntity<?> entity) {
		var builder = ScanRequest.builder();
		rejectStronglyConsistentIndexRead(request.getIndexName(), request.isConsistentRead());
		builder.consistentRead(request.isConsistentRead());
		builder.tableName(tableName);
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
			Map<String, AttributeValue> expressionAttributesToBuild = new HashMap<>(
					request.getExpressionAttributeValues().size());
			request.getExpressionAttributeValues().forEach(
					(k, v) -> expressionAttributesToBuild.put(k, dynamoDbConverter.convertToDynamoDbType(v, entity)));
			builder.expressionAttributeValues(expressionAttributesToBuild);
		}
		if (request.getFilterExpression() != null) {
			builder.filterExpression(request.getFilterExpression());
		}
		if (request.getExclusiveStartKey() != null) {
			Map<String, AttributeValue> exclusiveKey = new HashMap<>(request.getExclusiveStartKey().size());
			request.getExclusiveStartKey()
					.forEach((k, v) -> exclusiveKey.put(k, dynamoDbConverter.convertToDynamoDbType(v, entity)));
			builder.exclusiveStartKey(exclusiveKey);
		}
		if (request.getProjectionExpression() != null) {
			builder.projectionExpression(request.getProjectionExpression());
		}
		return builder.build();
	}
}
