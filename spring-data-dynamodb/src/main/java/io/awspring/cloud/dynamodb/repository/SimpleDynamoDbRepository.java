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
package io.awspring.cloud.dynamodb.repository;

import io.awspring.cloud.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.dynamodb.repository.support.DynamoDbEntityInformation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.util.Assert;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class SimpleDynamoDbRepository<T, ID> implements DynamoDbRepository<T, ID> {

	private final DynamoDbOperations dynamoDbOperations;
	private final DynamoDbEntityInformation<T, ID> entityInformation;

	public SimpleDynamoDbRepository(DynamoDbEntityInformation<T, ID> entityInformation,
			DynamoDbOperations dynamoDbOperations) {
		this.entityInformation = entityInformation;
		this.dynamoDbOperations = dynamoDbOperations;
	}

	@Override
	public <S extends T> S save(S entity) {
		Assert.notNull(entity, "Entity must not be null");
		return this.dynamoDbOperations.save(entity).getEntity();
	}

	@Override
	public <S extends T> List<S> saveAll(Iterable<S> entities) {
		Assert.notNull(entities, "Entities must not be null");
		List<S> result = new ArrayList<>();
		for (S entity : entities) {
			Assert.notNull(entity, "Entities must not contain null elements");
			result.add(save(entity));
		}
		return result;
	}

	@Override
	public Optional<T> findById(ID id) {
		Assert.notNull(id, "Id must not be null");
		if (id instanceof DynamoDbCompositeId compositeId) {
			return Optional.ofNullable(dynamoDbOperations.findById(compositeId.partitionKey(), compositeId.sortKey(),
					entityInformation.getJavaType()));
		}
		return Optional.ofNullable(dynamoDbOperations.findById(id, entityInformation.getJavaType()));
	}

	@Override
	public boolean existsById(ID id) {
		Assert.notNull(id, "Id must not be null");
		if (id instanceof DynamoDbCompositeId compositeId) {
			return dynamoDbOperations.existsById(compositeId.partitionKey(), compositeId.sortKey(),
					entityInformation.getJavaType());
		}
		return dynamoDbOperations.existsById(id, null, entityInformation.getJavaType());
	}

	@Override
	public List<T> findAll() {
		return dynamoDbOperations.findAll(entityInformation.getJavaType());
	}

	@Override
	public List<T> findAllById(Iterable<ID> ids) {
		Assert.notNull(ids, "Ids must not be null");

		Set<ID> remaining = new LinkedHashSet<>();
		for (ID id : ids) {
			Assert.notNull(id, "Ids must not contain null elements");
			remaining.add(id);
		}
		if (remaining.isEmpty()) {
			return new ArrayList<>();
		}

		List<T> result = new ArrayList<>();
		for (ID id : remaining) {
			findById(id).ifPresent(result::add);
		}
		return result;
	}

	@Override
	public long count() {
		return dynamoDbOperations.count(entityInformation.getJavaType());
	}

	@Override
	public void deleteById(ID id) {
		Assert.notNull(id, "Id must not be null");
		if (id instanceof DynamoDbCompositeId compositeId) {
			dynamoDbOperations.delete(entityInformation.getJavaType(), compositeId.partitionKey(),
					compositeId.sortKey());
		}
		else {
			dynamoDbOperations.delete(entityInformation.getJavaType(), id, null);
		}
	}

	@Override
	public void delete(T entity) {
		Assert.notNull(entity, "Entity must not be null");
		dynamoDbOperations.delete(entity);
	}

	@Override
	public void deleteAllById(Iterable<? extends ID> ids) {
		Assert.notNull(ids, "Ids must not be null");
		for (ID id : ids) {
			Assert.notNull(id, "Ids must not contain null elements");
			deleteById(id);
		}
	}

	@Override
	public void deleteAll(Iterable<? extends T> entities) {
		Assert.notNull(entities, "Entities must not be null");
		for (T entity : entities) {
			Assert.notNull(entity, "Entities must not contain null elements");
			delete(entity);
		}
	}

	@Override
	public void deleteAll() {
		for (T entity : findAll()) {
			delete(entity);
		}
	}

	@Override
	public <S extends T> S update(S entity) {
		Assert.notNull(entity, "Entity must not be null");
		return dynamoDbOperations.update(entity).getEntity();
	}
}
