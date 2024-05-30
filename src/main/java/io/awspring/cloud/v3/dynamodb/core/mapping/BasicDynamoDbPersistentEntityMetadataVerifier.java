package io.awspring.cloud.v3.dynamodb.core.mapping;

import org.springframework.data.mapping.MappingException;

import java.util.ArrayList;
import java.util.List;

public class BasicDynamoDbPersistentEntityMetadataVerifier implements DynamoDbPersistentEntityMetadataVerifier {


	@Override
	public void verify(DynamoDbPersistenceEntity<?> entity) throws MappingException {
		if (entity.getType().isInterface() || !entity.isAnnotationPresent(Table.class)) {
			return;
		}

		List<MappingException> exceptions = new ArrayList<>();

		List<DynamoDbPersistentProperty> idProperties = new ArrayList<>();

		entity.forEach(property -> {
			if (property.isIdProperty()) {
				idProperties.add(property);
			}
		});
		/**
			if (idProperties.size() != 1) {
				exceptions
					.add(new MappingException(String.format("@%s types must have only one primary attribute, if any; Found %s",
						Table.class.getSimpleName(), idProperties.size())));

				fail(entity, exceptions);
		 }
		 */
	}

	private static void fail(DynamoDbPersistenceEntity<?> entity, List<MappingException> exceptions) {
		throw new VerifierMappingExceptions(entity, exceptions);
	}
}
