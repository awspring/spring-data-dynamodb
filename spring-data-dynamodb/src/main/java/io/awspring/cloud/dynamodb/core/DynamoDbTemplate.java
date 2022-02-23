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
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.IndexKeySchema;
import io.awspring.cloud.dynamodb.core.mapping.TypeDiscriminatorRegistry;
import io.awspring.cloud.dynamodb.core.mapping.events.*;
import io.awspring.cloud.dynamodb.core.mapping.events.DynamoDbAfterConvertCallback;
import io.awspring.cloud.dynamodb.core.mapping.events.DynamoDbAfterConvertEvent;
import io.awspring.cloud.dynamodb.core.mapping.events.DynamoDbAfterDeleteEvent;
import io.awspring.cloud.dynamodb.core.mapping.events.DynamoDbAfterSaveCallback;
import io.awspring.cloud.dynamodb.core.mapping.events.DynamoDbAfterSaveEvent;
import io.awspring.cloud.dynamodb.core.mapping.events.DynamoDbAfterUpdateEvent;
import io.awspring.cloud.dynamodb.core.mapping.events.DynamoDbBeforeConvertCallback;
import io.awspring.cloud.dynamodb.core.mapping.events.DynamoDbBeforeDeleteEvent;
import io.awspring.cloud.dynamodb.core.mapping.events.DynamoDbBeforeSaveCallback;
import io.awspring.cloud.dynamodb.core.mapping.events.DynamoDbBeforeSaveEvent;
import io.awspring.cloud.dynamodb.core.mapping.events.DynamoDbBeforeUpdateEvent;
import io.awspring.cloud.dynamodb.core.mapping.events.DynamoDbMappingEvent;
import io.awspring.cloud.dynamodb.request.*;
import io.awspring.cloud.dynamodb.request.DynamoDbConditionRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbConditionRequestInterface;
import io.awspring.cloud.dynamodb.request.DynamoDbPageRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbQueryRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbQueryRequestInterface;
import io.awspring.cloud.dynamodb.request.DynamoDbScanRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbScanRequestInterface;
import io.awspring.cloud.dynamodb.request.DynamoDbUpdateExpressionRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbUpdateExpressionRequestInterface;
import io.awspring.cloud.dynamodb.request.IndexQueryBuilder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.callback.EntityCallbacks;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

public class DynamoDbTemplate extends DynamoDbAccessor
		implements DynamoDbOperations, ApplicationContextAware, ApplicationEventPublisherAware {

	private final DynamoDbConverter converter;
	private final EntityOperations entityOperations;
	private final StatementFactory statementFactory;

	private @Nullable EntityCallbacks entityCallbacks;
	private ApplicationEventPublisher eventPublisher;

	public DynamoDbTemplate(DynamoDbClient dynamoDbClient, DynamoDbConverter converter) {
		setDynamoDbClient(dynamoDbClient);
		this.converter = converter;
		this.entityOperations = new EntityOperations(converter.getMappingContext());
		this.statementFactory = new StatementFactory(converter);
	}

	@Override
	public void setApplicationContext(@Nullable ApplicationContext applicationContext) throws BeansException {
		if (entityCallbacks == null) {
			setEntityCallbacks(EntityCallbacks.create(applicationContext));
		}
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.eventPublisher = applicationEventPublisher;
	}

	public void setEntityCallbacks(@Nullable EntityCallbacks entityCallbacks) {
		this.entityCallbacks = entityCallbacks;
	}

	protected <E extends DynamoDbMappingEvent<T>, T> void maybeEmitEvent(E event) {
		if (this.eventPublisher != null) {
			this.eventPublisher.publishEvent(event);
		}
	}

	private static final int MAX_BATCH_WRITE_RETRIES = 8;
	private static final int BATCH_WRITE_MAX_ITEMS = 25;
	private static final long INITIAL_BACKOFF_MILLIS = 50L;
	private static final long MAX_BACKOFF_MILLIS = 5000L;

	@Override
	public <T> Iterable<T> saveAll(Iterable<T> entities) {
		Assert.notNull(entities, "Entities must not be null");

		List<T> entityList = new ArrayList<>();
		entities.forEach(entityList::add);
		if (entityList.isEmpty()) {
			return entityList;
		}
		Class<?> firstType = entityList.get(0).getClass();
		for (int i = 1; i < entityList.size(); i++) {
			if (!entityList.get(i).getClass().equals(firstType)) {
				throw new InvalidDataAccessApiUsageException(String.format(
						"saveAll() requires all entities to be of the same type; found %s at index 0 and %s at index %d",
						firstType.getName(), entityList.get(i).getClass().getName(), i));
			}
		}
		String tableName = getTableName(firstType);
		rejectIfSecondaryIndexView(getRequiredPersistentEntity(firstType), "saveAll()");

		List<T> preparedEntities = new ArrayList<>();
		List<WriteRequest> allWriteRequests = new ArrayList<>();
		for (T entity : entityList) {
			EntityOperations.AdaptibleEntity<T> source = getEntityOperations().forEntity(entity,
					getConverter().getConversionService());
			DynamoDbPersistentEntity<?> persistentEntity = source.getPersistentEntity();

			if (persistentEntity.hasVersionProperty()) {
				boolean wasNew = source.isNew();
				if (wasNew) {
					source.initializeVersionProperty();
				}
				else {
					source.incrementVersion();
				}
			}

			T entityToSave = maybeCallBeforeConvert(entity, tableName);
			entityToSave = maybeCallBeforeSave(entityToSave, tableName);
			maybeEmitEvent(new DynamoDbBeforeSaveEvent<>(entityToSave, tableName));
			preparedEntities.add(entityToSave);
			allWriteRequests.add(WriteRequest.builder().putRequest(doSaveAll(entityToSave, persistentEntity)).build());
		}

		List<List<WriteRequest>> chunks = new ArrayList<>();
		for (int i = 0; i < allWriteRequests.size(); i += BATCH_WRITE_MAX_ITEMS) {
			int end = Math.min(i + BATCH_WRITE_MAX_ITEMS, allWriteRequests.size());
			chunks.add(allWriteRequests.subList(i, end));
		}

		for (List<WriteRequest> chunk : chunks) {
			processBatchWithRetry(tableName, chunk);
		}

		List<T> savedEntities = new ArrayList<>(preparedEntities.size());
		for (T entityToSave : preparedEntities) {
			T saved = maybeCallAfterSave(entityToSave, tableName);
			maybeEmitEvent(new DynamoDbAfterSaveEvent<>(saved, tableName));
			savedEntities.add(saved);
		}
		return savedEntities;
	}

	private void processBatchWithRetry(String tableName, List<WriteRequest> writeRequests) {
		List<WriteRequest> remainingRequests = new ArrayList<>(writeRequests);
		long backoffMillis = INITIAL_BACKOFF_MILLIS;

		for (int attempt = 0; attempt < MAX_BATCH_WRITE_RETRIES; attempt++) {
			if (remainingRequests.isEmpty()) {
				return;
			}

			Map<String, List<WriteRequest>> requestItems = new HashMap<>();
			requestItems.put(tableName, remainingRequests);
			BatchWriteItemRequest batchWriteItemRequest = BatchWriteItemRequest.builder().requestItems(requestItems)
					.build();

			BatchWriteItemResponse response = execute("batchWriteItem", c -> c.batchWriteItem(batchWriteItemRequest));

			if (response.hasUnprocessedItems() && response.unprocessedItems().containsKey(tableName)) {
				remainingRequests = response.unprocessedItems().get(tableName);

				if (attempt < MAX_BATCH_WRITE_RETRIES - 1) {
					long jitter = java.util.concurrent.ThreadLocalRandom.current().nextLong(backoffMillis / 2 + 1);
					waitBeforeRetry(backoffMillis + jitter);
					backoffMillis = Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
				}
			}
			else {
				return;
			}
		}

		throw new DataAccessResourceFailureException(
				String.format("Failed to write %d item(s) to table '%s' after %d attempts due to unprocessed items",
						remainingRequests.size(), tableName, MAX_BATCH_WRITE_RETRIES));
	}

	protected void waitBeforeRetry(long millis) {
		try {
			java.util.concurrent.TimeUnit.MILLISECONDS.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new DataAccessResourceFailureException("Interrupted while waiting to retry batch write operation", e);
		}
	}

	@Override
	@Nullable
	public <T> T findById(Object id, Class<T> entityClass) {
		return findById(id, entityClass, Boolean.FALSE);
	}

	@Override
	@Nullable
	public <T> T findById(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass) {
		return findById(partitionKey, sortKey, entityClass, Boolean.FALSE);
	}

	@Override
	@Nullable
	public <T> T findById(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass, Boolean consistentRead) {
		Assert.notNull(partitionKey, "Must not be null");
		Assert.notNull(entityClass, "Entity type must not be null");

		DynamoDbPersistentEntity<?> entity = getRequiredPersistentEntity(entityClass);
		rejectIfSecondaryIndexView(entity, "findById()");
		String tableName = getTableName(entityClass);
		GetItemRequest getItemRequest = statementFactory.findByKeys(partitionKey, sortKey, tableName, entity,
				consistentRead);
		GetItemResponse getItemResponse = execute("getItem", c -> c.getItem(getItemRequest));
		return readAndConvert(entityClass, getItemResponse.item(), tableName);
	}

	@Override
	@Nullable
	public <T> T findById(Object id, Class<T> entityClass, Boolean consistentRead) {

		Assert.notNull(id, "Id must not be null");
		Assert.notNull(entityClass, "Entity type must not be null");

		DynamoDbPersistentEntity<?> entity = getRequiredPersistentEntity(entityClass);
		rejectIfSecondaryIndexView(entity, "findById()");
		String tableName = getTableName(entityClass);
		GetItemRequest getItemRequest = statementFactory.findByKey(id, tableName, entity, consistentRead);
		GetItemResponse getItemResponse = execute("getItem", c -> c.getItem(getItemRequest));
		return readAndConvert(entityClass, getItemResponse.item(), tableName);
	}

	@Override
	public <T> boolean existsById(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass) {
		Assert.notNull(partitionKey, "Partition key must not be null");
		Assert.notNull(entityClass, "Entity type must not be null");

		DynamoDbPersistentEntity<?> entity = getRequiredPersistentEntity(entityClass);
		rejectIfSecondaryIndexView(entity, "existsById()");
		String tableName = getTableName(entityClass);
		GetItemRequest request = statementFactory.existsByKey(partitionKey, sortKey, tableName, entity);
		GetItemResponse response = execute("getItem", c -> c.getItem(request));
		return response.hasItem() && !response.item().isEmpty();
	}

	private <T> PutRequest doSaveAll(T entityToSave, DynamoDbPersistentEntity<?> persistentEntity) {
		return statementFactory.insertAll(entityToSave, persistentEntity);
	}

	@Override
	public <T> EntityWriteResult<T> save(T entity) {
		return save(entity, new DynamoDbConditionRequest());
	}

	@Override
	public <T> EntityWriteResult<T> save(T entity, DynamoDbConditionRequest dynamoDBConditionRequest) {
		Assert.notNull(entity, "Entity must not be null");
		String tableName = getTableName(entity.getClass());
		EntityOperations.AdaptibleEntity<T> source = getEntityOperations().forEntity(entity,
				getConverter().getConversionService());
		DynamoDbPersistentEntity<?> persistentEntity = source.getPersistentEntity();
		rejectIfSecondaryIndexView(persistentEntity, "save()");

		Number previousVersion = null;
		DynamoDbConditionRequest augmentedConditionRequest = dynamoDBConditionRequest;
		boolean versionConditionApplied = persistentEntity.hasVersionProperty();
		if (versionConditionApplied) {
			boolean wasNew = source.isNew();
			if (wasNew) {
				source.initializeVersionProperty();
			}
			else {
				previousVersion = source.getVersion();
				source.incrementVersion();
			}
			augmentedConditionRequest = addVersionCondition(dynamoDBConditionRequest, persistentEntity, previousVersion,
					wasNew);
		}

		T entityToSave = maybeCallBeforeConvert(entity, tableName);
		entityToSave = maybeCallBeforeSave(entityToSave, tableName);
		PutItemRequest request = statementFactory.insert(entityToSave, persistentEntity, tableName,
				augmentedConditionRequest);
		maybeEmitEvent(new DynamoDbBeforeSaveEvent<T>(entityToSave, tableName));

		try {
			PutItemResponse putItemResponse = getCurrentDynamoDbClient().putItem(request);
			T saved = maybeCallAfterSave(entityToSave, tableName);
			maybeEmitEvent(new DynamoDbAfterSaveEvent<>(saved, tableName));
			return EntityWriteResult.of(saved);
		}
		catch (ConditionalCheckFailedException e) {
			if (versionConditionApplied) {
				throw new OptimisticLockingFailureException(
						"Version mismatch during save for entity " + entity.getClass().getName()
								+ (previousVersion != null ? " (expected version: " + previousVersion + ")" : ""),
						e);
			}
			throw new DataIntegrityViolationException("Conditional check failed during save for entity "
					+ entity.getClass().getName() + " -- the supplied condition expression was not satisfied", e);
		}
		catch (RuntimeException e) {
			throw translateIfPossible("putItem", e);
		}
	}

	@Override
	public <T> EntityWriteResult<T> save(T entity, DynamoDbConditionRequestInterface builderInterface) {
		return save(entity, builderInterface.build(DynamoDbConditionRequest.Builder.request()));
	}

	@Override
	public <T> EntityWriteResult<T> insert(T entity) {
		Assert.notNull(entity, "Entity must not be null");
		String tableName = getTableName(entity.getClass());
		EntityOperations.AdaptibleEntity<T> source = getEntityOperations().forEntity(entity,
				getConverter().getConversionService());
		DynamoDbPersistentEntity<?> persistentEntity = source.getPersistentEntity();
		rejectIfSecondaryIndexView(persistentEntity, "insert()");

		if (persistentEntity.hasVersionProperty() && source.isNew()) {
			source.initializeVersionProperty();
		}
		DynamoDbConditionRequest insertCondition = buildInsertCondition(persistentEntity);

		T entityToSave = maybeCallBeforeConvert(entity, tableName);
		entityToSave = maybeCallBeforeSave(entityToSave, tableName);
		PutItemRequest request = statementFactory.insert(entityToSave, persistentEntity, tableName, insertCondition);
		maybeEmitEvent(new DynamoDbBeforeSaveEvent<>(entityToSave, tableName));

		try {
			PutItemResponse putItemResponse = getCurrentDynamoDbClient().putItem(request);
			T saved = maybeCallAfterSave(entityToSave, tableName);
			maybeEmitEvent(new DynamoDbAfterSaveEvent<>(saved, tableName));
			return EntityWriteResult.of(saved);
		}
		catch (ConditionalCheckFailedException e) {
			throw new DuplicateKeyException("Entity of type " + entity.getClass().getName()
					+ " already exists (insert requires a non-existent partition key)", e);
		}
		catch (RuntimeException e) {
			throw translateIfPossible("putItem", e);
		}
	}

	private DynamoDbConditionRequest buildInsertCondition(DynamoDbPersistentEntity<?> persistentEntity) {
		String partitionKeyColumn = persistentEntity.getIdProperty().getColumnName();
		Map<String, String> names = new HashMap<>();
		names.put("#__pk", partitionKeyColumn);
		return DynamoDbConditionRequest.Builder.request().withConditionExpression("attribute_not_exists(#__pk)")
				.withExpressionAttributeNames(names).build();
	}

	@Override
	public void delete(Object entity) {
		Assert.notNull(entity, "Entity must not be null");
		DynamoDbPersistentEntity<?> persistentEntity = getRequiredPersistentEntity(entity.getClass());
		rejectIfSecondaryIndexView(persistentEntity, "delete()");
		String tableName = getTableName(entity.getClass());
		DeleteItemRequest request = statementFactory.delete(entity, persistentEntity, tableName);
		maybeEmitEvent(new DynamoDbBeforeDeleteEvent<>(entity, tableName));
		execute("deleteItem", c -> c.deleteItem(request));
		maybeEmitEvent(new DynamoDbAfterDeleteEvent<>(entity, tableName));
	}

	@Override
	public <T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey) {
		delete(entityClass, primaryKey, sortKey, new DynamoDbConditionRequest());
	}

	@Override
	public <T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey,
			DynamoDbConditionRequest dynamoDBConditionRequest) {
		DynamoDbPersistentEntity<?> entity = getRequiredPersistentEntity(entityClass);
		rejectIfSecondaryIndexView(entity, "delete()");
		String tableName = getTableName(entityClass);
		DeleteItemRequest request = statementFactory.delete(primaryKey, sortKey, entity, tableName,
				dynamoDBConditionRequest);
		maybeEmitEvent(new DynamoDbBeforeDeleteEvent<>(entityClass, tableName));
		execute("deleteItem", c -> c.deleteItem(request));
		maybeEmitEvent(new DynamoDbAfterDeleteEvent<>(entityClass, tableName));
	}

	@Override
	public <T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey,
			DynamoDbConditionRequestInterface builderInterface) {
		delete(entityClass, primaryKey, sortKey, builderInterface.build(DynamoDbConditionRequest.Builder.request()));
	}

	@Override
	public DynamoDbConverter getConverter() {
		return this.converter;
	}

	@Override
	public <T> EntityQueryResult<List<T>> query(Class<T> entityClass, DynamoDbQueryRequest qr,
			DynamoDbPageRequest dynamoDBPageRequest) {
		String tableName = getTableName(entityClass);
		DynamoDbPersistentEntity basicDynamoDbPersistentEntity = getRequiredPersistentEntity(entityClass);
		QueryRequest queryRequest = statementFactory.query(tableName, basicDynamoDbPersistentEntity, qr,
				dynamoDBPageRequest);
		QueryResponse queryResponse = execute("query", c -> c.query(queryRequest));
		List<T> listToBeReturned = new ArrayList<>(queryResponse.items().size());
		queryResponse.items().forEach(item -> listToBeReturned.add(readAndConvert(entityClass, item, tableName)));
		if (queryResponse.hasLastEvaluatedKey()) {
			return EntityQueryResult.of(listToBeReturned, queryResponse.count(),
					toCursor(queryResponse.lastEvaluatedKey()));
		}

		return EntityQueryResult.of(listToBeReturned, queryResponse.count());
	}

	@Override
	public <T> EntityQueryResult<List<T>> query(Class<T> entityClass, DynamoDbQueryRequestInterface builderInterface,
			DynamoDbPageRequest dynamoDBPageRequest) {
		return query(entityClass, builderInterface.build(DynamoDbQueryRequest.Builder.request()), dynamoDBPageRequest);
	}

	@Override
	public <T> EntityQueryResult<List<T>> scan(Class<T> entityClass, DynamoDbScanRequest scanRequest) {
		String tableName = getTableName(entityClass);
		DynamoDbPersistentEntity basicDynamoDbPersistentEntity = getRequiredPersistentEntity(entityClass);
		var scan = statementFactory.scan(tableName, scanRequest, basicDynamoDbPersistentEntity);
		ScanResponse scanResponse = execute("scan", c -> c.scan(scan));
		List<T> listToBeReturned = new ArrayList<>(scanResponse.items().size());
		scanResponse.items().forEach(item -> listToBeReturned.add(readAndConvert(entityClass, item, tableName)));
		if (scanResponse.hasLastEvaluatedKey()) {
			return EntityQueryResult.of(listToBeReturned, scanResponse.count(),
					toCursor(scanResponse.lastEvaluatedKey()));
		}
		return EntityQueryResult.of(listToBeReturned, scanResponse.count());

	}

	@Override
	public <T> EntityQueryResult<List<T>> scan(Class<T> entityClass, DynamoDbScanRequestInterface builderInterface) {
		return scan(entityClass, builderInterface.build(DynamoDbScanRequest.Builder.builder()));
	}

	@Override
	public <T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass,
			List<Object> values) {
		return executeStatement(statement, nextToken, entityClass, values, Boolean.FALSE);
	}

	@Override
	public <T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass) {
		return executeStatement(statement, nextToken, entityClass, null);
	}

	@Override
	public <T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass,
			List<Object> values, Boolean consistentRead) {
		Assert.notNull(statement, "Statement must not be null");
		Assert.notNull(entityClass, "Entity type must not be null");

		DynamoDbPersistentEntity<?> entity = getRequiredPersistentEntity(entityClass);
		ExecuteStatementRequest executeStatementRequest = statementFactory.executeStatementRequest(statement, nextToken,
				values, entity, consistentRead);
		ExecuteStatementResponse executeStatementResponse = execute("executeStatement",
				c -> c.executeStatement(executeStatementRequest));
		String tableName = getTableName(entityClass);
		List<T> listToBeReturned = new ArrayList<>(executeStatementResponse.items().size());
		executeStatementResponse.items()
				.forEach(item -> listToBeReturned.add(readAndConvert(entityClass, item, tableName)));
		return EntityReadResult.of(listToBeReturned, executeStatementResponse.nextToken());
	}

	@Override
	public <T> EntityWriteResult<T> update(T entity) {
		Assert.notNull(entity, "Entity must not be null");
		String tableName = getTableName(entity.getClass());
		DynamoDbPersistentEntity dynamoDbPersistenceEntity = getRequiredPersistentEntity(entity.getClass());
		rejectIfSecondaryIndexView(dynamoDbPersistenceEntity, "update()");

		Number previousVersion = null;
		if (dynamoDbPersistenceEntity.hasVersionProperty()) {
			EntityOperations.AdaptibleEntity<T> source = getEntityOperations().forEntity(entity,
					getConverter().getConversionService());
			previousVersion = source.getVersion();
			source.incrementVersion();
		}

		UpdateItemRequest updateItemRequest = statementFactory.update(entity, tableName, dynamoDbPersistenceEntity,
				previousVersion);
		maybeEmitEvent(new DynamoDbBeforeUpdateEvent<>(entity, tableName));

		try {
			UpdateItemResponse updateItemResponse = getCurrentDynamoDbClient().updateItem(updateItemRequest);
			maybeEmitEvent(new DynamoDbAfterUpdateEvent<>(entity, tableName));
			return EntityWriteResult.of(entity);
		}
		catch (ConditionalCheckFailedException e) {
			throw new OptimisticLockingFailureException(
					"Version mismatch during update for entity " + entity.getClass().getName()
							+ (previousVersion != null ? " (expected version: " + previousVersion + ")" : ""),
					e);
		}
		catch (RuntimeException e) {
			throw translateIfPossible("updateItem", e);
		}
	}

	@Override
	public <T> EntityWriteResult<T> update(Object partitionKey, @Nullable Object sortKey,
			DynamoDbUpdateExpressionRequest dynamoDBUpdateExpressionRequest, Class<T> entityClass) {
		DynamoDbPersistentEntity dynamoDbPersistenceEntity = getRequiredPersistentEntity(entityClass);
		rejectIfSecondaryIndexView(dynamoDbPersistenceEntity, "update()");
		String tableName = getTableName(entityClass);
		UpdateItemRequest updateItemRequest = statementFactory.update(partitionKey, sortKey,
				dynamoDBUpdateExpressionRequest, tableName, dynamoDbPersistenceEntity);
		maybeEmitEvent(new DynamoDbBeforeUpdateEvent<>(entityClass, tableName));
		UpdateItemResponse updateItemResponse = execute("updateItem", c -> c.updateItem(updateItemRequest));
		maybeEmitEvent(new DynamoDbAfterUpdateEvent<>(entityClass, tableName));
		return EntityWriteResult.of(converter.read(entityClass, updateItemResponse.attributes()));
	}

	@Override
	public <T> EntityWriteResult<T> update(Object partitionKey, @Nullable Object sortKey,
			DynamoDbUpdateExpressionRequestInterface builderInterface, Class<T> entityClass) {
		return update(partitionKey, sortKey, builderInterface.build(DynamoDbUpdateExpressionRequest.Builder.builder()),
				entityClass);
	}

	public String getTableName(Class<?> entityClass) {
		return getEntityOperations().getTableName(entityClass);
	}

	@Override
	public <T> long count(Class<T> entityClass) {
		DynamoDbPersistentEntity<?> entity = getRequiredPersistentEntity(entityClass);
		DynamoDbScanRequest.Builder scanBuilder = DynamoDbScanRequest.Builder.builder();
		if (entity.isSecondaryIndexView()) {
			scanBuilder.withIndexName(entity.getIndexName());
		}
		return count(entityClass, scanBuilder.build());
	}

	@Override
	public <T> long count(Class<T> entityClass, DynamoDbScanRequest scanRequest) {
		return new ScanPager<>(entityClass, this::scan).countAll(scanRequest);
	}

	@Override
	public <T> boolean exists(Class<T> entityClass, DynamoDbScanRequest scanRequest) {
		return new ScanPager<>(entityClass, this::scan).exists(scanRequest, results -> !results.isEmpty());
	}

	@Override
	public <T> List<T> findAll(Class<T> entityClass) {
		DynamoDbPersistentEntity<?> entity = getRequiredPersistentEntity(entityClass);
		DynamoDbScanRequest.Builder scanBuilder = DynamoDbScanRequest.Builder.builder();
		if (entity.isSecondaryIndexView()) {
			scanBuilder.withIndexName(entity.getIndexName());
		}
		return new ScanPager<>(entityClass, this::scan).collectAll(scanBuilder.build());
	}

	@Override
	public <T> IndexQueryBuilder<T> query(Class<T> entityClass, String indexName) {
		DynamoDbPersistentEntity<?> entity = getRequiredPersistentEntity(entityClass);
		IndexKeySchema keySchema = entity.getKeySchema();
		IndexQueryBuilder.QueryExecutor<T> executor = (clazz, request, pageRequest) -> query(clazz, request,
				pageRequest != null ? pageRequest : DynamoDbPageRequest.of(null));
		return new IndexQueryBuilder<>(entityClass, indexName, keySchema, executor);
	}

	@Override
	public EntityQueryResult<List<Object>> queryPolymorphic(String tableName, DynamoDbQueryRequest queryRequest,
			DynamoDbPageRequest dynamoDBPageRequest) {
		Collection<DynamoDbPersistentEntity<?>> entities = entitiesForTable(tableName);
		DynamoDbPageRequest pageRequest = dynamoDBPageRequest != null ? dynamoDBPageRequest
				: DynamoDbPageRequest.of(null);
		QueryRequest request = statementFactory.query(tableName, entities.iterator().next(), queryRequest, pageRequest);
		QueryResponse response = execute("query", c -> c.query(request));
		return toPolymorphicResult(response.items(), response.count(),
				response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null, entities);
	}

	@Override
	public EntityQueryResult<List<Object>> scanPolymorphic(String tableName, DynamoDbScanRequest scanRequest) {
		Collection<DynamoDbPersistentEntity<?>> entities = entitiesForTable(tableName);
		var scan = statementFactory.scan(tableName, scanRequest, entities.iterator().next());
		ScanResponse response = execute("scan", c -> c.scan(scan));
		return toPolymorphicResult(response.items(), response.count(),
				response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null, entities);
	}

	private Collection<DynamoDbPersistentEntity<?>> entitiesForTable(String tableName) {
		Collection<DynamoDbPersistentEntity<?>> entities = ((DynamoDbMappingContext) converter.getMappingContext())
				.getEntitiesForTable(tableName);
		Assert.state(!entities.isEmpty(), () -> "No @Table entity registered for table '" + tableName
				+ "' -- cannot resolve polymorphic result types.");
		return entities;
	}

	private EntityQueryResult<List<Object>> toPolymorphicResult(List<Map<String, AttributeValue>> items, Integer count,
			@Nullable Map<String, AttributeValue> lastEvaluatedKey, Collection<DynamoDbPersistentEntity<?>> entities) {
		TypeDiscriminatorRegistry registry = TypeDiscriminatorRegistry.fromEntities(entities);
		List<Object> results = new ArrayList<>(items.size());
		for (Map<String, AttributeValue> item : items) {
			if (item != null && !item.isEmpty()) {
				results.add(converter.read(registry.resolve(item), item));
			}
		}
		if (lastEvaluatedKey != null) {
			return EntityQueryResult.of(results, count, toCursor(lastEvaluatedKey));
		}
		return EntityQueryResult.of(results, count);
	}

	protected EntityOperations getEntityOperations() {
		return this.entityOperations;
	}

	private <R> R execute(String task, Function<DynamoDbClient, R> action) {
		try {
			return action.apply(getCurrentDynamoDbClient());
		}
		catch (RuntimeException ex) {
			throw translateIfPossible(task, ex);
		}
	}

	private RuntimeException translateIfPossible(String task, RuntimeException ex) {
		if (ex instanceof DataAccessException) {
			return ex;
		}
		DataAccessException translated = translate(task, null, ex);
		return translated != null ? translated : ex;
	}

	protected <T> T maybeCallBeforeConvert(T object, String tableName) {

		if (null != entityCallbacks) {
			return (T) entityCallbacks.callback(DynamoDbBeforeConvertCallback.class, object, tableName);
		}

		return object;
	}

	protected <T> T maybeCallBeforeSave(T object, String tableName) {

		if (null != entityCallbacks) {
			return (T) entityCallbacks.callback(DynamoDbBeforeSaveCallback.class, object, tableName);
		}

		return object;
	}

	protected <T> T maybeCallAfterSave(T object, String tableName) {

		if (null != entityCallbacks) {
			return (T) entityCallbacks.callback(DynamoDbAfterSaveCallback.class, object, tableName);
		}

		return object;
	}

	protected <T> T maybeCallAfterConvert(T object, String tableName) {

		if (null != entityCallbacks) {
			return (T) entityCallbacks.callback(DynamoDbAfterConvertCallback.class, object, tableName);
		}

		return object;
	}

	@Nullable
	private <T> T readAndConvert(Class<T> entityClass, Map<String, AttributeValue> item, String tableName) {
		T entity = converter.read(entityClass, item);
		if (entity == null) {
			return null;
		}
		T result = maybeCallAfterConvert(entity, tableName);
		maybeEmitEvent(new DynamoDbAfterConvertEvent<>(result, tableName));
		return result;
	}

	private static Map<String, Object> toCursor(Map<String, AttributeValue> lastEvaluatedKey) {
		Map<String, Object> cursor = new HashMap<>(lastEvaluatedKey.size());
		lastEvaluatedKey.forEach((name, value) -> cursor.put(name, cursorValue(value)));
		return cursor;
	}

	@Nullable
	private static Object cursorValue(AttributeValue value) {
		if (value.n() != null) {
			return new BigDecimal(value.n());
		}
		if (value.s() != null) {
			return value.s();
		}
		if (value.b() != null) {
			return value.b().asByteArray();
		}
		if (value.bool() != null) {
			return value.bool();
		}
		return null;
	}

	private DynamoDbPersistentEntity<?> getRequiredPersistentEntity(Class<?> entityType) {
		return getEntityOperations().getRequiredPersistentEntity(entityType);
	}

	private static void rejectIfSecondaryIndexView(DynamoDbPersistentEntity<?> entity, String operation) {
		if (entity.isSecondaryIndexView()) {
			throw new InvalidDataAccessApiUsageException(String.format(
					"%s is a @SecondaryIndex view (index '%s') and does not support %s -- it is read-only and "
							+ "query-only. Writes go through the base @Table entity that this "
							+ "index projects; a GetItem-style lookup is not possible on a DynamoDB index at all "
							+ "(use a query() instead).",
					entity.getType().getName(), entity.getIndexName(), operation));
		}
	}

	private DynamoDbConditionRequest addVersionCondition(DynamoDbConditionRequest originalRequest,
			DynamoDbPersistentEntity<?> persistentEntity, Number previousVersion, boolean isNew) {

		String versionColumnName = persistentEntity.getRequiredVersionProperty().getColumnName();
		String versionCondition;
		Map<String, String> names = new HashMap<>();
		Map<String, Object> values = new HashMap<>();

		if (originalRequest.getExpressionAttributeNames() != null) {
			names.putAll(originalRequest.getExpressionAttributeNames());
		}
		if (originalRequest.getExpressionAttributeValues() != null) {
			values.putAll(originalRequest.getExpressionAttributeValues());
		}

		String namePlaceholder = uniquePlaceholder(names.keySet(), "#__version");
		names.put(namePlaceholder, versionColumnName);

		if (isNew) {
			versionCondition = "attribute_not_exists(" + namePlaceholder + ")";
		}
		else {
			String valuePlaceholder = uniquePlaceholder(values.keySet(), ":__prevVersion");
			values.put(valuePlaceholder, previousVersion);
			versionCondition = namePlaceholder + " = " + valuePlaceholder;
		}

		String combinedCondition;
		if (originalRequest.getConditionExpression() != null && !originalRequest.getConditionExpression().isEmpty()) {
			combinedCondition = "(" + originalRequest.getConditionExpression() + ") AND (" + versionCondition + ")";
		}
		else {
			combinedCondition = versionCondition;
		}

		return DynamoDbConditionRequest.Builder.request().withConditionExpression(combinedCondition)
				.withExpressionAttributeNames(names).withExpressionAttributeValues(values).build();
	}

	private static String uniquePlaceholder(java.util.Set<String> reserved, String preferred) {
		if (!reserved.contains(preferred)) {
			return preferred;
		}
		int suffix = 1;
		String candidate;
		do {
			candidate = preferred + suffix++;
		}
		while (reserved.contains(candidate));
		return candidate;
	}

}
