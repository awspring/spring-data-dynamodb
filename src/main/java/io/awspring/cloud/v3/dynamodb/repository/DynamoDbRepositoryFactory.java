package io.awspring.cloud.v3.dynamodb.repository;

import io.awspring.cloud.v3.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbPersistenceEntity;
import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.cloud.v3.dynamodb.repository.support.DynamoDbEntityInformation;
import io.awspring.cloud.v3.dynamodb.repository.support.MappingDynamoDbEntityInformation;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.util.Assert;

public class DynamoDbRepositoryFactory extends RepositoryFactorySupport {

	private final MappingContext<? extends DynamoDbPersistenceEntity<?>, DynamoDbPersistentProperty> mappingContext;

	private final DynamoDbOperations operations;

	public DynamoDbRepositoryFactory(DynamoDbOperations operations) {
		Assert.notNull(operations, "Operation cannot be null!");

		this.mappingContext = operations.getConverter().getMappingContext();
		this.operations = operations;
	}

	@Override
	public <T, ID> DynamoDbEntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
		DynamoDbPersistenceEntity<?> entity = mappingContext.getRequiredPersistentEntity(domainClass);
		return new MappingDynamoDbEntityInformation<>((DynamoDbPersistenceEntity<T>) entity, operations.getConverter());
	}

	@Override
	protected Object getTargetRepository(RepositoryInformation information) {
		DynamoDbEntityInformation<?, Object> entityInformation = getEntityInformation(information.getDomainType());
		return getTargetRepositoryViaReflection(information, entityInformation, operations);
	}

	@Override
	protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
		return SimpleDynamoDbRepository.class;
	}
}
