package io.awspring.cloud.v3.dynamodb.core.mapping;

import org.springframework.data.mapping.PersistentEntity;

public interface DynamoDbPersistenceEntity<T> extends PersistentEntity<T, DynamoDbPersistentProperty> {

	String getTableName();

}
