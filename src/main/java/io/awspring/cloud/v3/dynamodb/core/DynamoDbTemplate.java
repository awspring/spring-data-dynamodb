package io.awspring.cloud.v3.dynamodb.core;

import io.awspring.cloud.v3.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbPersistenceEntity;
import io.awspring.cloud.v3.dynamodb.core.mapping.events.*;
import io.awspring.cloud.v3.dynamodb.request.*;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.mapping.callback.EntityCallbacks;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DynamoDbTemplate used for Entity and DynamoDb communication.
 *
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbTemplate implements DynamoDbOperations, ApplicationContextAware, ApplicationEventPublisherAware {

	private DynamoDbClient dynamoDbClient;
	private DynamoDbConverter converter;
	private final EntityOperations entityOperations;
	private StatementFactory statementFactory;

	private @Nullable EntityCallbacks entityCallbacks;
	private ApplicationEventPublisher eventPublisher;


	public DynamoDbTemplate(DynamoDbClient dynamoDbClient, DynamoDbConverter converter) {
		this.dynamoDbClient = dynamoDbClient;
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

	@Override
	public <T> Iterable<T> saveAll(Iterable<T> entities, Class ent) {
		List<WriteRequest> putRequests = new ArrayList<>();
		String tableName = getTableName(ent);
		Map<String, List<WriteRequest>> mapRequest = new HashMap<>();
		entities.forEach(entity -> {
			putRequests.add(WriteRequest.builder().putRequest(doSaveAll(entity, tableName)).build());
		});
		mapRequest.put(tableName, putRequests);
		BatchWriteItemRequest batchWriteItemRequest = BatchWriteItemRequest.builder().requestItems(mapRequest).build();
		maybeEmitEvent(new DynamoDbBeforeSaveEvent<>(entities, tableName));
		dynamoDbClient.batchWriteItem(batchWriteItemRequest);
		maybeEmitEvent(new DynamoDbAfterSaveEvent<>(entities, tableName));
		return entities;
	}

	@Override
	public <T> T getEntityByKey(Object id, Class<T> entityClass) {
		return getEntityByKey(id, entityClass, Boolean.FALSE);
	}

	@Override
	public <T> T findEntityByKeys(String partitionKey, String sortKey, Class<T> entityClass) {
		return findEntityByKeys(partitionKey, sortKey, entityClass, Boolean.FALSE);
	}

	@Override
	public <T> T findEntityByKeys(String partitionKey, String sortKey, Class<T> entityClass, Boolean consistentRead) {
		Assert.notNull(partitionKey, "Must not be null");
		Assert.notNull(entityClass, "Entity type must not be null");

		DynamoDbPersistenceEntity<?> entity = getRequiredPersistentEntity(entityClass);
		String tableName = getTableName(entityClass);
		GetItemRequest getItemRequest = statementFactory.findByKeys(partitionKey, sortKey, tableName, entity, consistentRead);
		GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);
		return converter.read(entityClass, getItemResponse.item());
	}


	@Override
	public <T> T getEntityByKey(Object id, Class<T> entityClass, Boolean consistentRead) {

		Assert.notNull(id, "Id must not be null");
		Assert.notNull(entityClass, "Entity type must not be null");

		DynamoDbPersistenceEntity<?> entity = getRequiredPersistentEntity(entityClass);
		String tableName = getTableName(entityClass);
		GetItemRequest getItemRequest = statementFactory.findByKey(id, tableName, entity, consistentRead);
		GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);
		return converter.read(entityClass, getItemResponse.item());
	}


	private <T> PutRequest doSaveAll(T entity, String tableName) {
		EntityOperations.AdaptibleEntity<T> source = getEntityOperations().forEntity(entity, getConverter().getConversionService());
		T entityToSave = maybeCallBeforeSave(entity, tableName);
        return statementFactory.insertAll(entityToSave, source.getPersistentEntity());
	}

	@Override
	public <T> EntityWriteResult<T> save(T entity) {
		return save(entity, new DynamoDBConditionRequest());
	}

	@Override
	public <T> EntityWriteResult<T> save(T entity, DynamoDBConditionRequest dynamoDBConditionRequest) {
		String tableName = getTableName(entity.getClass());
		EntityOperations.AdaptibleEntity<T> source = getEntityOperations().forEntity(entity, getConverter().getConversionService());
		T entityToSave = maybeCallBeforeSave(entity, tableName);
		PutItemRequest request = statementFactory.insert(entityToSave, source.getPersistentEntity(), tableName, dynamoDBConditionRequest);
		maybeEmitEvent(new DynamoDbBeforeSaveEvent<T>(entity, tableName));
		PutItemResponse putItemResponse = dynamoDbClient.putItem(request);
		maybeEmitEvent(new DynamoDbAfterSaveEvent<>(entityToSave, tableName));
		return EntityWriteResult.of(putItemResponse.attributes(), entity);
	}


	@Override
	public void delete(Object entity) {
		String tableName = getTableName(entity.getClass());
		DeleteItemRequest request = statementFactory.delete(entity, getRequiredPersistentEntity(entity.getClass()), tableName);
		maybeEmitEvent(new DynamoDbBeforeDeleteEvent<>(entity, tableName));
		dynamoDbClient.deleteItem(request);
		maybeEmitEvent(new DynamoDbAfterDeleteEvent<>(entity, tableName));
	}

	@Override
	public <T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey) {
		delete(entityClass, primaryKey, sortKey, new DynamoDBConditionRequest());
	}

	@Override
	public <T> void delete(Class<T> entityClass, Object primaryKey, @Nullable  Object sortKey, DynamoDBConditionRequest dynamoDBConditionRequest) {
		String tableName = getTableName(entityClass);
		DeleteItemRequest request = statementFactory.delete(primaryKey, sortKey, getRequiredPersistentEntity(entityClass), tableName, dynamoDBConditionRequest);
		maybeEmitEvent(new DynamoDbBeforeDeleteEvent<>(entityClass, tableName));
		dynamoDbClient.deleteItem(request);
		maybeEmitEvent(new DynamoDbAfterDeleteEvent<>(entityClass, tableName));
	}

	@Override
	public DynamoDbConverter getConverter() {
		return this.converter;
	}

	@Override
	public <T> EntityQueryResult<List<T>> query(Class<T> entityClass, DynamoDBQueryRequest qr, DynamoDBPageRequest dynamoDBPageRequest) {
		String tableName = getTableName(entityClass);
		DynamoDbPersistenceEntity basicDynamoDbPersistenceEntity = getRequiredPersistentEntity(entityClass);
		QueryRequest queryRequest = statementFactory.query(tableName, basicDynamoDbPersistenceEntity, qr, dynamoDBPageRequest);
		QueryResponse queryResponse = dynamoDbClient.query(queryRequest);
		List<T> listToBeReturned = new ArrayList<>(queryResponse.items().size());
		queryResponse.items().forEach(item ->
				{
					if (item != null) {
						listToBeReturned.add(converter.read(entityClass, item));
					}
				}
		);
		return EntityQueryResult.of(listToBeReturned, queryResponse.count().longValue());
	}

	@Override
	public <T> EntityQueryResult<List<T>> scan(Class<T> entityClass, DynamoDbScanRequest scanRequest) {
		String tableName = getTableName(entityClass.getClass());
		DynamoDbPersistenceEntity basicDynamoDbPersistenceEntity = getRequiredPersistentEntity(entityClass);
		var scan = statementFactory.scan(tableName, scanRequest, basicDynamoDbPersistenceEntity);
		ScanResponse scanResponse = dynamoDbClient.scan(scan);
		List<T> listToBeReturned = new ArrayList<>(scanResponse.items().size());
		scanResponse.items().forEach(item ->
				{
					if (item != null) {
						listToBeReturned.add(converter.read(entityClass, item));
					}
				}
		);
		return EntityQueryResult.of(listToBeReturned, scanResponse.count().longValue());

	}

	@Override
	public <T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass, List<Object> values) {
		return executeStatement(statement, nextToken, entityClass, values, Boolean.FALSE);
	}

	@Override
	public <T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass) {
		return executeStatement(statement, nextToken, entityClass, null);
	}

	@Override
	public <T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass, List<Object> values, Boolean consistentRead) {
		Assert.notNull(statement, "Statement must not be null");
		Assert.notNull(entityClass, "Entity type must not be null");

		DynamoDbPersistenceEntity<?> entity = getRequiredPersistentEntity(entityClass);
		ExecuteStatementRequest executeStatementRequest = statementFactory.executeStatementRequest(statement, nextToken, values, entity, consistentRead);
		ExecuteStatementResponse executeStatementResponse = dynamoDbClient.executeStatement(executeStatementRequest);
		List<T> listToBeReturned = new ArrayList<>(executeStatementResponse.items().size());
		executeStatementResponse.items().forEach(item ->
			{
				if (item != null) {
					listToBeReturned.add(converter.read(entityClass, item));
				}
			}
		);
		return EntityReadResult.of(listToBeReturned, executeStatementResponse.nextToken());
	}

	@Override
	public <T> EntityWriteResult<T> update(T entity) {
		String tableName = getTableName(entity.getClass());
		DynamoDbPersistenceEntity dynamoDbPersistenceEntity = getRequiredPersistentEntity(entity.getClass());
		UpdateItemRequest updateItemRequest = statementFactory.update(entity, tableName, dynamoDbPersistenceEntity);
		maybeEmitEvent(new DynamoDbBeforeUpdateEvent<>(entity, tableName));
		UpdateItemResponse updateItemResponse = dynamoDbClient.updateItem(updateItemRequest);
		maybeEmitEvent(new DynamoDbAfterUpdateEvent<>(entity, tableName));
		return EntityWriteResult.of(updateItemResponse.attributes(), entity);
	}

	@Override
	public <T> EntityWriteResult<T> update(Map<String, Object> keys, DynamoDBUpdateExpressionRequest dynamoDBUpdateExpressionRequest, Class<T> entityClass) {
		String tableName = getTableName(entityClass.getClass());
		DynamoDbPersistenceEntity dynamoDbPersistenceEntity = getRequiredPersistentEntity(entityClass.getClass());
		UpdateItemRequest updateItemRequest = statementFactory.update(keys, dynamoDBUpdateExpressionRequest, tableName, dynamoDbPersistenceEntity);
		maybeEmitEvent(new DynamoDbBeforeUpdateEvent<>(entityClass, tableName));
		UpdateItemResponse updateItemResponse = dynamoDbClient.updateItem(updateItemRequest);
		maybeEmitEvent(new DynamoDbAfterUpdateEvent<>(entityClass, tableName));
		return EntityWriteResult.of(updateItemResponse.attributes(), converter.read(entityClass, updateItemResponse.attributes()));
	}

	public String getTableName(Class<?> entityClass) {
		return getEntityOperations().getTableName(entityClass);
	}

	protected EntityOperations getEntityOperations() {
		return this.entityOperations;
	}

	protected <T> T maybeCallBeforeSave(T object, String tableName) {

		if (null != entityCallbacks) {
			return (T) entityCallbacks.callback(DynamoDbBeforeSaveCallback.class, object, tableName);
		}

		return object;
	}

	private DynamoDbPersistenceEntity<?> getRequiredPersistentEntity(Class<?> entityType) {
		return getEntityOperations().getRequiredPersistentEntity(entityType);
	}

}

