package io.awspring.cloud.v3.dynamodb.core;

import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbPersistenceEntity;
import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbPersistentProperty;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.ConvertingPropertyAccessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.util.Map;

/**
 * Common data access operations performed on an entity using a {@link MappingContext} containing mapping metadata.
 *
 * @author Matej Nedic
 * @see DynamoDbTemplate
 * @since 3.0
 */
public class EntityOperations {

	private final MappingContext<? extends DynamoDbPersistenceEntity<?>, DynamoDbPersistentProperty> mappingContext;

	public EntityOperations(MappingContext<? extends DynamoDbPersistenceEntity<?>, DynamoDbPersistentProperty> mappingContext) {
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

	protected MappingContext<? extends DynamoDbPersistenceEntity<?>, DynamoDbPersistentProperty> getMappingContext() {
		return this.mappingContext;
	}

	String getTableName(Class<?> entityClass) {
		return getRequiredPersistentEntity(entityClass).getTableName();
	}

	DynamoDbPersistenceEntity<?> getRequiredPersistentEntity(Class<?> entityClass) {
		return getMappingContext().getRequiredPersistentEntity(ClassUtils.getUserClass(entityClass));
	}

	interface Entity<T> {

		/**
		 * Returns whether the entity is versioned, i.e. if it contains a version property.
		 *
		 * @return
		 */
		default boolean isVersionedEntity() {
			return false;
		}

		/**
		 * Returns the value of the version if the entity has a version property, {@literal null} otherwise.
		 *
		 * @return
		 */
		@Nullable
		Object getVersion();

		/**
		 * Returns the underlying bean.
		 *
		 * @return
		 */
		T getBean();

		/**
		 * Returns whether the entity is considered to be new.
		 *
		 * @return
		 */
		boolean isNew();
	}

	/**
	 * Information and commands on an entity.
	 */
	interface AdaptibleEntity<T> extends Entity<T> {


		/**
		 * Initializes the version property of the of the current entity if available.
		 *
		 * @return the entity with the version property updated if available.
		 */
		T initializeVersionProperty();

		/**
		 * Increments the value of the version property if available.
		 *
		 * @return the entity with the version property incremented if available.
		 */
		T incrementVersion();

		/**
		 * Returns the current version value if the entity has a version property.
		 *
		 * @return the current version or {@literal null} in case it's uninitialized or the entity doesn't expose a version
		 *         property.
		 */
		@Nullable
		Number getVersion();


		/**
		 * Returns the {@link DynamoDbPersistenceEntity}.
		 *
		 * @return the {@link DynamoDbPersistenceEntity}.
		 */
		DynamoDbPersistenceEntity<?> getPersistentEntity();

	}

	private static class MappedEntity<T> implements Entity<T> {

		private final DynamoDbPersistenceEntity<?> entity;
		private final PersistentPropertyAccessor<T> propertyAccessor;

		protected MappedEntity(DynamoDbPersistenceEntity<?> entity, PersistentPropertyAccessor<T> propertyAccessor) {
			this.entity = entity;
			this.propertyAccessor = propertyAccessor;
		}

		private static <T> MappedEntity<T> of(T bean,
											  MappingContext<? extends DynamoDbPersistenceEntity<?>, DynamoDbPersistentProperty> context) {

			DynamoDbPersistenceEntity<?> entity = context.getRequiredPersistentEntity(bean.getClass());
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

		private final DynamoDbPersistenceEntity<?> entity;
		private final ConvertingPropertyAccessor<T> propertyAccessor;

		private static <T> AdaptibleEntity<T> of(T bean,
												 MappingContext<? extends DynamoDbPersistenceEntity<?>, DynamoDbPersistentProperty> mappingContext,
												 ConversionService conversionService) {

			DynamoDbPersistenceEntity<?> entity = mappingContext.getRequiredPersistentEntity(bean.getClass());

			PersistentPropertyAccessor<T> propertyAccessor = entity.getPropertyAccessor(bean);

			return new AdaptibleMappedEntity<>(entity, new ConvertingPropertyAccessor<>(propertyAccessor, conversionService));
		}

		private AdaptibleMappedEntity(DynamoDbPersistenceEntity<?> entity, ConvertingPropertyAccessor<T> propertyAccessor) {

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
		public DynamoDbPersistenceEntity<?> getPersistentEntity() {
			return this.entity;
		}

		private String getVersionColumnName() {
			return this.entity.getRequiredVersionProperty().getColumnName();
		}
	}
}
