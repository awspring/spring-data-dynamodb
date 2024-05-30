package io.awspring.cloud.v3.dynamodb.repository;

import io.awspring.cloud.v3.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.v3.dynamodb.core.mapping.BasicDynamoDbPersistenceEntity;
import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.cloud.v3.dynamodb.repository.support.DynamoDbEntityInformation;
import org.springframework.data.mapping.context.MappingContext;

import java.util.Optional;

public class SimpleDynamoDbRepository<T, KEY> implements DynamoDbRepository<T, KEY> {


	private final MappingContext<? extends BasicDynamoDbPersistenceEntity<?>, DynamoDbPersistentProperty> mappingContext;
	private final DynamoDbOperations dynamoDbOperations;
	private final DynamoDbEntityInformation<T, KEY> entityInformation;

	public SimpleDynamoDbRepository(DynamoDbEntityInformation<T, KEY> entityInformation, DynamoDbOperations dynamoDbOperations, MappingContext<? extends BasicDynamoDbPersistenceEntity<?>, DynamoDbPersistentProperty> mappingContext) {
		this.entityInformation = entityInformation;
		this.mappingContext = mappingContext;
		this.dynamoDbOperations = dynamoDbOperations;
	}

	@Override
	public <S extends T> S save(S entity) {
		return this.dynamoDbOperations.save(entity).getEntity();
	}

	@Override
	public <S extends T> Iterable<S> saveAll(Iterable<S> entities) {
		return dynamoDbOperations.saveAll(entities, entityInformation.getJavaType());
	}

	@Override
	public Optional<T> findByPartitionKey(KEY key) {
		return Optional.of(dynamoDbOperations.getEntityByKey(key,this.entityInformation.getJavaType()));
	}

	@Override
	public void delete(T entity) {
		dynamoDbOperations.delete(entityInformation.getJavaType());
	}


	@Override
	public <S extends T> S update(S entity) {
		return dynamoDbOperations.update(entity).getEntity();
	}

}
