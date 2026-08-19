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
package io.awspring.spring.data.dynamodb.repository.support;

import io.awspring.spring.data.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentProperty;
import org.jspecify.annotations.Nullable;
import org.springframework.data.repository.core.support.PersistentEntityInformation;
import org.springframework.util.Assert;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class MappingDynamoDbEntityInformation<T, ID> extends PersistentEntityInformation<T, ID>
		implements DynamoDbEntityInformation<T, ID> {

	private final DynamoDbPersistentEntity<T> entityMetadata;

	private final DynamoDbConverter converter;

	private final boolean secondaryIndexView;

	public MappingDynamoDbEntityInformation(DynamoDbPersistentEntity<T> entity, DynamoDbConverter converter) {
		this(entity, converter, false);
	}

	public MappingDynamoDbEntityInformation(DynamoDbPersistentEntity<T> entity, DynamoDbConverter converter,
			boolean secondaryIndexView) {
		super(entity);

		this.entityMetadata = entity;
		this.converter = converter;
		this.secondaryIndexView = secondaryIndexView;
	}

	@Override
	public @Nullable String getIdAttribute() {
		DynamoDbPersistentProperty idProperty = this.entityMetadata.getIdProperty();
		return idProperty != null ? idProperty.getName() : null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Class<ID> getIdType() {
		if (this.secondaryIndexView || this.entityMetadata.getIdProperty() == null) {
			return (Class<ID>) (Class<?>) Void.class;
		}
		return super.getIdType();
	}

	@Override
	public ID getId(T entity) {
		Assert.notNull(entity, "Entity must not be null");

		DynamoDbPersistentProperty idProperty = this.entityMetadata.getIdProperty();

		return idProperty != null ? (ID) this.entityMetadata.getIdentifierAccessor(entity).getIdentifier()
				: (ID) converter.getId(entity, entityMetadata);
	}

	@Override
	public String getTableName() {
		return this.entityMetadata.getTableName();
	}
}
