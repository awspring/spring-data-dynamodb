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

import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentProperty;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.ConvertingPropertyAccessor;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class EntityOperations {

	private final MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext;

	public EntityOperations(
			MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext) {
		this.mappingContext = mappingContext;
	}

	public <T> Entity<T> forEntity(T entity) {

		Assert.notNull(entity, "Bean must not be null!");

		return MappedEntity.of(entity, mappingContext);
	}

	public <T> AdaptibleEntity<T> forEntity(T entity, ConversionService conversionService) {

		Assert.notNull(entity, "Bean must not be null!");
		Assert.notNull(conversionService, "ConversionService must not be null!");

		return AdaptibleMappedEntity.of(entity, mappingContext, conversionService);
	}

	protected MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> getMappingContext() {
		return this.mappingContext;
	}

	String getTableName(Class<?> entityClass) {
		return getRequiredPersistentEntity(entityClass).getTableName();
	}

	DynamoDbPersistentEntity<?> getRequiredPersistentEntity(Class<?> entityClass) {
		return getMappingContext().getRequiredPersistentEntity(ClassUtils.getUserClass(entityClass));
	}

	interface Entity<T> {

		default boolean isVersionedEntity() {
			return false;
		}

		@Nullable
		Object getVersion();

		T getBean();

		boolean isNew();
	}

	interface AdaptibleEntity<T> extends Entity<T> {

		T initializeVersionProperty();

		T incrementVersion();

		@Nullable
		Number getVersion();

		DynamoDbPersistentEntity<?> getPersistentEntity();

	}

	private static class MappedEntity<T> implements Entity<T> {

		private final DynamoDbPersistentEntity<?> entity;
		private final PersistentPropertyAccessor<T> propertyAccessor;

		protected MappedEntity(DynamoDbPersistentEntity<?> entity, PersistentPropertyAccessor<T> propertyAccessor) {
			this.entity = entity;
			this.propertyAccessor = propertyAccessor;
		}

		private static <T> MappedEntity<T> of(T bean,
				MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> context) {

			DynamoDbPersistentEntity<?> entity = context.getRequiredPersistentEntity(bean.getClass());
			PersistentPropertyAccessor<T> propertyAccessor = entity.getPropertyAccessor(bean);

			return new MappedEntity<>(entity, propertyAccessor);
		}

		@Override
		public T getBean() {
			return this.propertyAccessor.getBean();
		}

		@Override
		public boolean isNew() {
			return this.entity.isNew(getBean());
		}

		@Override
		public boolean isVersionedEntity() {
			return this.entity.hasVersionProperty();
		}

		@Override
		@Nullable
		public Object getVersion() {
			return this.propertyAccessor.getProperty(this.entity.getRequiredVersionProperty());
		}
	}

	private static class AdaptibleMappedEntity<T> extends MappedEntity<T> implements AdaptibleEntity<T> {

		private final DynamoDbPersistentEntity<?> entity;
		private final ConvertingPropertyAccessor<T> propertyAccessor;

		private static <T> AdaptibleEntity<T> of(T bean,
				MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext,
				ConversionService conversionService) {

			DynamoDbPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(bean.getClass());

			PersistentPropertyAccessor<T> propertyAccessor = entity.getPropertyAccessor(bean);

			return new AdaptibleMappedEntity<>(entity,
					new ConvertingPropertyAccessor<>(propertyAccessor, conversionService));
		}

		private AdaptibleMappedEntity(DynamoDbPersistentEntity<?> entity,
				ConvertingPropertyAccessor<T> propertyAccessor) {

			super(entity, propertyAccessor);

			this.entity = entity;
			this.propertyAccessor = propertyAccessor;
		}

		@Override
		public T initializeVersionProperty() {

			if (this.entity.hasVersionProperty()) {

				DynamoDbPersistentProperty versionProperty = this.entity.getRequiredVersionProperty();

				this.propertyAccessor.setProperty(versionProperty, versionProperty.getType().isPrimitive() ? 1 : 0);
			}

			return this.propertyAccessor.getBean();
		}

		@Override
		public T incrementVersion() {

			DynamoDbPersistentProperty versionProperty = this.entity.getRequiredVersionProperty();

			Number version = getVersion();
			Number nextVersion = version == null ? 0 : version.longValue() + 1;

			this.propertyAccessor.setProperty(versionProperty, nextVersion);

			return this.propertyAccessor.getBean();
		}

		@Override
		@Nullable
		public Number getVersion() {

			DynamoDbPersistentProperty versionProperty = this.entity.getRequiredVersionProperty();

			return this.propertyAccessor.getProperty(versionProperty, Number.class);
		}

		@Override
		public DynamoDbPersistentEntity<?> getPersistentEntity() {
			return this.entity;
		}

		private String getVersionColumnName() {
			return this.entity.getRequiredVersionProperty().getColumnName();
		}
	}
}
