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
package io.awspring.spring.data.dynamodb.core.mapping;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.mapping.context.AbstractMappingContext;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbMappingContext
		extends AbstractMappingContext<BasicDynamoDbPersistentEntity<?>, DynamoDbPersistentProperty>
		implements ApplicationContextAware, BeanClassLoaderAware {

	private @Nullable ApplicationContext applicationContext;

	private final DynamoDbPersistentEntityMetadataVerifier verifier = new CompositeDynamoDbPersistentEntityMetadataVerifier();

	private final NamingStrategy namingStrategy = NamingStrategy.INSTANCE;

	private final Map<String, Set<DynamoDbPersistentEntity<?>>> entitySetsByTableName = new ConcurrentHashMap<>();

	private final Set<BasicDynamoDbPersistentEntity<?>> tableEntities = ConcurrentHashMap.newKeySet();

	@Override
	protected Optional<BasicDynamoDbPersistentEntity<?>> addPersistentEntity(TypeInformation<?> typeInformation) {

		Optional<BasicDynamoDbPersistentEntity<?>> optional = shouldCreatePersistentEntityFor(typeInformation)
				? super.addPersistentEntity(typeInformation)
				: Optional.empty();

		optional.ifPresent(entity -> {

			if (!entity.isSecondaryIndexView() && !entity.isAggregateView()) {
				Set<DynamoDbPersistentEntity<?>> entities = this.entitySetsByTableName
						.computeIfAbsent(entity.getTableName(), string -> ConcurrentHashMap.newKeySet());
				entities.add(entity);
			}
			this.tableEntities.add(entity);

		});

		return optional;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		super.setApplicationContext(applicationContext);
		this.applicationContext = applicationContext;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
	}

	@Override
	protected <T> BasicDynamoDbPersistentEntity<?> createPersistentEntity(TypeInformation<T> typeInformation) {
		BasicDynamoDbPersistentEntity<T> entity = new BasicDynamoDbPersistentEntity<>(typeInformation, getVerifier());

		entity.setMappingContext(this);
		Optional.ofNullable(this.applicationContext).ifPresent(entity::setApplicationContext);

		return entity;
	}

	@Override
	protected DynamoDbPersistentProperty createPersistentProperty(Property property,
			BasicDynamoDbPersistentEntity<?> owner, SimpleTypeHolder simpleTypeHolder) {

		BasicDynamoDbPersistentProperty persistentProperty = new CachingDynamoDbPersistentProperty(property, owner,
				simpleTypeHolder);

		persistentProperty.setNamingStrategy(this.namingStrategy);
		Optional.ofNullable(this.applicationContext).ifPresent(persistentProperty::setApplicationContext);

		return persistentProperty;
	}

	public DynamoDbPersistentEntityMetadataVerifier getVerifier() {
		return this.verifier;
	}

	public Set<String> distinctBaseTableNames() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(this.entitySetsByTableName.keySet()));
	}

	public Collection<DynamoDbPersistentEntity<?>> getEntitiesForTable(String tableName) {
		Set<DynamoDbPersistentEntity<?>> entities = this.entitySetsByTableName.get(tableName);
		return entities == null ? Collections.emptySet() : Collections.unmodifiableCollection(entities);
	}

}
