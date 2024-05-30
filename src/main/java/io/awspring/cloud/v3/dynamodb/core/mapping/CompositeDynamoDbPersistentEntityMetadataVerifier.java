package io.awspring.cloud.v3.dynamodb.core.mapping;

import org.springframework.data.mapping.MappingException;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Collection;

public class CompositeDynamoDbPersistentEntityMetadataVerifier implements DynamoDbPersistentEntityMetadataVerifier {

	private Collection<DynamoDbPersistentEntityMetadataVerifier> verifiers;

	/**
	 * Create a new {@link DynamoDbPersistentEntityMetadataVerifier} using default entity and primary key
	 * verifiers.
	 *
	 * @see BasicDynamoDbPersistentEntityMetadataVerifier
	 */
	public CompositeDynamoDbPersistentEntityMetadataVerifier() {
		this(Arrays.asList(new BasicDynamoDbPersistentEntityMetadataVerifier()));
	}

	/**
	 * Create a new {@link CompositeDynamoDbPersistentEntityMetadataVerifier} for the given {@code verifiers}
	 *
	 * @param verifiers must not be {@literal null}.
	 */
	private CompositeDynamoDbPersistentEntityMetadataVerifier(
		Collection<DynamoDbPersistentEntityMetadataVerifier> verifiers) {

		Assert.notNull(verifiers, "Verifiers must not be null");

		this.verifiers = verifiers;
	}

	@Override
	public void verify(DynamoDbPersistenceEntity<?> entity) throws MappingException {
		verifiers.forEach(verifier -> verifier.verify(entity));
	}
}
