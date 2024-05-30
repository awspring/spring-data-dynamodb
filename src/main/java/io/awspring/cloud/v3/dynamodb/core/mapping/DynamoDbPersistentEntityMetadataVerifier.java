package io.awspring.cloud.v3.dynamodb.core.mapping;

import org.springframework.data.mapping.MappingException;

@FunctionalInterface
public interface DynamoDbPersistentEntityMetadataVerifier {

	void verify(DynamoDbPersistenceEntity<?> entity) throws MappingException;
}
