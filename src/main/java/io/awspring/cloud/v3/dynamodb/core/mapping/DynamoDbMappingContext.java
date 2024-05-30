package io.awspring.cloud.v3.dynamodb.core.mapping;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.mapping.context.AbstractMappingContext;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of a {@link MappingContext} for DynamoDB using {@link DynamoDbPersistenceEntity} and
 * {@link DynamoDbPersistentProperty} as primary abstractions.
 *
 * @author Matej Nedic
 */
public class DynamoDbMappingContext extends AbstractMappingContext<BasicDynamoDbPersistenceEntity<?>, DynamoDbPersistentProperty>
	implements ApplicationContextAware, BeanClassLoaderAware {

	private @Nullable ApplicationContext applicationContext;

	private @Nullable ClassLoader beanClassLoader;

	private DynamoDbPersistentEntityMetadataVerifier verifier = new CompositeDynamoDbPersistentEntityMetadataVerifier();

	private NamingStrategy namingStrategy = NamingStrategy.INSTANCE;

	// caches
	private final Map<String, Set<DynamoDbPersistenceEntity<?>>> entitySetsByTableName = new ConcurrentHashMap<>();

	private final Set<BasicDynamoDbPersistenceEntity<?>> tableEntities = ConcurrentHashMap.newKeySet();


	@Override
	protected Optional<BasicDynamoDbPersistenceEntity<?>> addPersistentEntity(TypeInformation<?> typeInformation) {

		Optional<BasicDynamoDbPersistenceEntity<?>> optional = shouldCreatePersistentEntityFor(typeInformation)
			? super.addPersistentEntity(typeInformation)
			: Optional.empty();

		optional.ifPresent(entity -> {

			Set<DynamoDbPersistenceEntity<?>> entities = this.entitySetsByTableName.computeIfAbsent(entity.getTableName(),
				string -> ConcurrentHashMap.newKeySet());

			entities.add(entity);

		});

		return optional;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	@Override
	protected <T> BasicDynamoDbPersistenceEntity<?> createPersistentEntity(TypeInformation<T> typeInformation) {
		BasicDynamoDbPersistenceEntity<T> entity =  new BasicDynamoDbPersistenceEntity<T>(typeInformation, getVerifier());

		Optional.ofNullable(this.applicationContext).ifPresent(entity::setApplicationContext);

		return entity;
	}

	@Override
	protected DynamoDbPersistentProperty createPersistentProperty(Property property, BasicDynamoDbPersistenceEntity<?> owner, SimpleTypeHolder simpleTypeHolder) {

		BasicDynamoDbPersistentProperty persistentProperty = new CachingDynamoDbPersistentProperty(property, owner, simpleTypeHolder);

		persistentProperty.setNamingStrategy(this.namingStrategy);
		Optional.ofNullable(this.applicationContext).ifPresent(persistentProperty::setApplicationContext);

		return persistentProperty;
	}

	public DynamoDbPersistentEntityMetadataVerifier getVerifier() {
		return this.verifier;
	}

	public Collection<BasicDynamoDbPersistenceEntity<?>> getTableEntities() {
		return Collections.unmodifiableCollection(this.tableEntities);
	}

}
