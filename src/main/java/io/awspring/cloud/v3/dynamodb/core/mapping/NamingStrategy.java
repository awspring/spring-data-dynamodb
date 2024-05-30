package io.awspring.cloud.v3.dynamodb.core.mapping;

import org.springframework.util.Assert;

import java.util.function.UnaryOperator;

public interface NamingStrategy {

	NamingStrategy INSTANCE = new NamingStrategy() {};



	default String getTableName(DynamoDbPersistenceEntity<?> entity) {

		Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");

		return entity.getType().getSimpleName();
	}

	default String getUserDefinedTypeName(DynamoDbPersistenceEntity<?> entity) {

		Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");

		return entity.getType().getSimpleName();
	}


	default String getColumnName(DynamoDbPersistentProperty property) {

		Assert.notNull(property, "DynamoDbPersistentProperty must not be null");

		return property.getName();
	}

}
