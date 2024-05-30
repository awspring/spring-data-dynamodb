package io.awspring.cloud.v3.dynamodb.repository.support;

import io.awspring.cloud.v3.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbPersistenceEntity;
import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbPersistentProperty;
import org.springframework.data.repository.core.support.PersistentEntityInformation;
import org.springframework.util.Assert;

public class MappingDynamoDbEntityInformation<T, ID> extends PersistentEntityInformation<T, ID>
	implements DynamoDbEntityInformation<T, ID> {

	private final DynamoDbPersistenceEntity<T> entityMetadata;

	private final DynamoDbConverter converter;

	public MappingDynamoDbEntityInformation(DynamoDbPersistenceEntity<T> entity, DynamoDbConverter converter) {
		super(entity);

		this.entityMetadata = entity;
		this.converter = converter;
	}

	@Override
	public String getIdAttribute() {
		return this.entityMetadata.getRequiredIdProperty().getName();
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
