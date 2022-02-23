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
package io.awspring.cloud.dynamodb.core.converter;

import io.awspring.cloud.dynamodb.core.mapping.*;
import io.awspring.cloud.dynamodb.core.mapping.BasicDynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.cloud.dynamodb.core.mapping.KeyRole;
import io.awspring.cloud.dynamodb.core.mapping.KeyTemplateResolver;
import io.awspring.cloud.dynamodb.core.mapping.TypeDiscriminatorRegistry;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.mapping.*;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.ConvertingPropertyAccessor;
import org.springframework.data.mapping.model.EntityInstantiator;
import org.springframework.data.mapping.model.ParameterValueProvider;
import org.springframework.data.mapping.model.PersistentEntityParameterValueProvider;
import org.springframework.data.mapping.model.PropertyValueProvider;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;

public class MappingDynamoDbConverter extends AbstractDynamoDbConverter
		implements ApplicationContextAware, BeanClassLoaderAware, InitializingBean {

	private @Nullable ClassLoader beanClassLoader;

	private @Nullable ApplicationContext applicationContext;

	private final DynamoDbMappingContext mappingContext;

	public MappingDynamoDbConverter(DynamoDbMappingContext mappingContext) {
		super(newConversionService());
		DynamoDbConversions conversions = new DynamoDbConversions(Collections.emptyList());
		this.mappingContext = mappingContext;
		this.setCustomConversions(conversions);
	}

	private static ConversionService newConversionService() {
		return new DefaultConversionService();
	}

	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();
	}

	@Override
	@Nullable
	public Object getId(Object object, DynamoDbPersistentEntity<?> entity) {

		Assert.notNull(object, "Object instance must not be null");
		Assert.notNull(entity, "DynamoDbPersistentEntity must not be null");

		ConvertingPropertyAccessor<?> propertyAccessor = newConvertingPropertyAccessor(object, entity);

		DynamoDbPersistentProperty idProperty = entity.getIdProperty();

		Assert.notNull(idProperty, "ID property cannot be null.");
		return propertyAccessor.getProperty(idProperty);
	}

	@Override
	public void write(Object obj, Map<String, AttributeValue> items, DynamoDbPersistentEntity<?> persistentEntity) {

		Assert.notNull(obj, "obj must not be null");
		Assert.notNull(items, "items must not be null");
		Assert.notNull(persistentEntity, "DynamoDbPersistentEntity must not be null");

		ConvertingPropertyAccessor convertingPropertyAccessor = newConvertingPropertyAccessor(obj, persistentEntity);
		for (DynamoDbPersistentProperty property : persistentEntity) {
			if (propertyValueConversions.hasValueConverter(property)) {
				items.put(property.getColumnName(),
						writeWithConverter(property, convertingPropertyAccessor.getProperty(property)));
			}
			else if (property.isSpecialType() && !property.serializeAsJson()) {
				Object nested = convertingPropertyAccessor.getProperty(property);
				if (nested != null) {
					Class<?> innerType = transformClassToBeanClassLoaderClass(property.getTypeOfProperty());
					DynamoDbPersistentEntity<?> innerEntity = getMappingContext()
							.getRequiredPersistentEntity(innerType);
					write(nested, items, innerEntity);
				}
			}
			else {
				writeInternal(convertingPropertyAccessor, property, items);
			}
		}

		writeSortKeyTemplates(obj, items, persistentEntity);
	}

	private void writeSortKeyTemplates(Object obj, Map<String, AttributeValue> items,
			DynamoDbPersistentEntity<?> persistentEntity) {
		KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(persistentEntity);

		for (String column : resolver.templateIndexes()) {
			boolean conflictsWithSortKey = persistentEntity.getKeySchema().sortKeys().stream()
					.anyMatch(sortKeyProperty -> sortKeyProperty.getColumnName().equals(column));
			Assert.state(!conflictsWithSortKey,
					() -> "Entity " + persistentEntity.getType().getName()
							+ " declares both a @SortKey and a @SortKeyTemplate targeting column \"" + column
							+ "\" -- the two are mutually exclusive per column");
			String composed = resolver.compose(column, obj, getConversionService());
			items.put(resolver.columnFor(column), AttributeValue.builder().s(composed).build());
		}
	}

	private void writeInternal(ConvertingPropertyAccessor<?> convertingPropertyAccessor,
			DynamoDbPersistentProperty property, Map<String, AttributeValue> attributeValueMap) {
		attributeValueMap.put(property.getColumnName(),
				toAttributeValue(convertingPropertyAccessor.getProperty(property), property.serializeAsJson()));
	}

	@SuppressWarnings("unchecked")
	private AttributeValue writeWithConverter(DynamoDbPersistentProperty property, @Nullable Object value) {
		PropertyValueConverter<Object, AttributeValue, ValueConversionContext<DynamoDbPersistentProperty>> converter = this.propertyValueConversions
				.getValueConverter(property);
		DynamoDbConversionContext context = new DynamoDbConversionContext(property);
		AttributeValue converted = value != null ? converter.write(value, context) : converter.writeNull(context);
		return converted != null ? converted : AttributeValue.builder().nul(Boolean.TRUE).build();
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private Object readWithConverter(DynamoDbPersistentProperty property, @Nullable AttributeValue value) {
		PropertyValueConverter<Object, AttributeValue, ValueConversionContext<DynamoDbPersistentProperty>> converter = this.propertyValueConversions
				.getValueConverter(property);
		DynamoDbConversionContext context = new DynamoDbConversionContext(property);
		if (value == null || value.nul() != null) {
			return converter.readNull(context);
		}
		return converter.read(value, context);
	}

	private void writeInternalUpdate(ConvertingPropertyAccessor<?> convertingPropertyAccessor,
			DynamoDbPersistentProperty property, Map<String, AttributeValueUpdate> attributeValueMap) {
		attributeValueMap.put(property.getColumnName(),
				AttributeValueUpdate.builder().value(
						toAttributeValue(convertingPropertyAccessor.getProperty(property), property.serializeAsJson()))
						.build());
	}

	@Override
	public void delete(Object objectToDelete, Map<String, AttributeValue> keys,
			DynamoDbPersistentEntity<?> persistenceEntity) {
		fetchKeysAndPopulate(objectToDelete, keys, persistenceEntity);
	}

	@Override
	public void findByKey(Object key, Map<String, AttributeValue> keys, DynamoDbPersistentEntity<?> persistenceEntity) {
		DynamoDbPersistentProperty persistentProperty = persistenceEntity.getIdProperty();
		keys.put(persistentProperty.getColumnName(), toAttributeValue(key, false));
	}

	@Override
	public void findByKeys(Object partitionKey, @Nullable Object sortKey, Map<String, AttributeValue> keys,
			DynamoDbPersistentEntity<?> persistenceEntity) {
		DynamoDbPersistentProperty persistentProperty = persistenceEntity.getIdProperty();
		keys.put(persistentProperty.getColumnName(), toAttributeValue(partitionKey, false));
		if (sortKey != null) {
			DynamoDbPersistentProperty rangeKey = persistenceEntity.getKeySchema().singleSortKey();
			Assert.notNull(rangeKey, "Entity has no base-table sort key to bind a sortKey argument to");
			keys.put(rangeKey.getColumnName(), toAttributeValue(sortKey, false));
		}
	}

	@Override
	public void update(Object objectToUpdate, Map<String, AttributeValue> keys, DynamoDbPersistentEntity<?> entity,
			Map<String, AttributeValueUpdate> values) {
		Assert.notNull(objectToUpdate, "objectToUpdate must not be null");
		Assert.notNull(keys, "keys must not be null");
		Assert.notNull(entity, "entity must not be null");
		Assert.notNull(values, "values must not be null");
		fetchKeysAndPopulate(objectToUpdate, keys, entity);
		updateRecursive(objectToUpdate, values, entity);
		writeSortKeyTemplateUpdates(objectToUpdate, values, entity);
	}

	public void updateRecursive(Object objectToUpdate, Map<String, AttributeValueUpdate> values,
			DynamoDbPersistentEntity<?> entity) {
		ConvertingPropertyAccessor<?> accessor = newConvertingPropertyAccessor(objectToUpdate, entity);
		for (DynamoDbPersistentProperty property : entity) {
			if (property.isIdProperty() || isBaseTableSortKey(property)) {
				continue;
			}
			if (propertyValueConversions.hasValueConverter(property)) {
				values.put(property.getColumnName(), AttributeValueUpdate.builder()
						.value(writeWithConverter(property, accessor.getProperty(property))).build());
			}
			else if (property.isSpecialType() && !property.serializeAsJson()) {
				Object nested = accessor.getProperty(property);
				if (nested != null) {
					Class<?> beanClassLoaderClass = transformClassToBeanClassLoaderClass(property.getTypeOfProperty());
					DynamoDbPersistentEntity<?> persis = getMappingContext()
							.getRequiredPersistentEntity(beanClassLoaderClass);
					updateRecursive(nested, values, persis);
				}
			}
			else {
				writeInternalUpdate(accessor, property, values);
			}
		}
	}

	private void writeSortKeyTemplateUpdates(Object obj, Map<String, AttributeValueUpdate> values,
			DynamoDbPersistentEntity<?> entity) {
		KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);
		for (String column : resolver.templateIndexes()) {
			String composed = resolver.compose(column, obj, getConversionService());
			values.put(resolver.columnFor(column),
					AttributeValueUpdate.builder().value(AttributeValue.builder().s(composed).build()).build());
		}
	}

	private static boolean isBaseTableSortKey(DynamoDbPersistentProperty property) {
		for (KeyRole role : property.getKeyRoles()) {
			if (role.keyType() == KeyRole.KeyType.SORT) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private Object readInnerClass(DynamoDbPersistentProperty property, Map<String, AttributeValue> source,
			DynamoDbPersistentEntity<?> owner) {
		Class<?> innerType = transformClassToBeanClassLoaderClass(property.getTypeOfProperty());
		DynamoDbPersistentEntity<?> innerEntity = getMappingContext().getRequiredPersistentEntity(innerType);

		String startsWith = property.startsWith();
		String endsWith = property.endsWith();
		boolean routed = (startsWith != null && !startsWith.isEmpty()) || (endsWith != null && !endsWith.isEmpty());

		if (routed) {
			String sortKeyValue = baseTableSortKeyValue(source, owner);
			if (sortKeyValue == null) {
				return null;
			}
			if (startsWith != null && !startsWith.isEmpty() && !sortKeyValue.startsWith(startsWith)) {
				return null;
			}
			if (endsWith != null && !endsWith.isEmpty() && !sortKeyValue.endsWith(endsWith)) {
				return null;
			}
			return read(source, innerEntity);
		}

		return hasAnyColumnPresent(innerEntity, source) ? read(source, innerEntity) : null;
	}

	private static boolean hasAnyColumnPresent(DynamoDbPersistentEntity<?> innerEntity,
			Map<String, AttributeValue> source) {
		for (DynamoDbPersistentProperty property : innerEntity) {
			AttributeValue value = source.get(property.getColumnName());
			if (value != null && value.nul() == null) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private static String baseTableSortKeyValue(Map<String, AttributeValue> source, DynamoDbPersistentEntity<?> owner) {
		DynamoDbPersistentProperty sortKey = owner.getKeySchema().singleSortKey();
		if (sortKey == null) {
			return null;
		}
		AttributeValue value = source.get(sortKey.getColumnName());
		return value != null ? value.s() : null;
	}

	@Override
	@Nullable
	public <R> R read(Class<R> type, Map<String, AttributeValue> source) {
		Assert.notNull(type, "type must not be null");
		Assert.notNull(source, "source must not be null");
		if (source.isEmpty()) {
			return null;
		}
		DynamoDbPersistentEntity<R> entity = (DynamoDbPersistentEntity<R>) getMappingContext()
				.getRequiredPersistentEntity(type);
		return read(source, entity);
	}

	@Nullable
	public Object read(Map<String, AttributeValue> source) {
		Assert.notNull(source, "source must not be null");
		if (source.isEmpty()) {
			return null;
		}
		String tableName = resolveTableNameFromRegisteredEntities(source);
		Collection<DynamoDbPersistentEntity<?>> candidates = this.mappingContext.getEntitiesForTable(tableName);
		TypeDiscriminatorRegistry registry = TypeDiscriminatorRegistry.fromEntities(candidates);
		Class<?> resolvedType = registry.resolve(source);
		DynamoDbPersistentEntity<?> entity = getMappingContext().getRequiredPersistentEntity(resolvedType);
		return read(source, entity);
	}

	private String resolveTableNameFromRegisteredEntities(Map<String, AttributeValue> source) {
		for (BasicDynamoDbPersistentEntity<?> candidate : this.mappingContext.getPersistentEntities()) {
			String column = candidate.getDiscriminatorColumn();
			if (column.isEmpty()) {
				continue;
			}
			AttributeValue tagValue = source.get(column);
			if (tagValue != null && tagValue.s() != null && tagValue.s().equals(candidate.getTypeName())) {
				return candidate.getTableName();
			}
		}
		throw new MappingException("Cannot resolve a table for a class-less read: no registered entity's "
				+ "@Table(discriminator=...) column matched a value equal to that entity's typeName(). "
				+ "A class-less read requires at least one entity to opt into a discriminator. Item keys: "
				+ source.keySet());
	}

	public void stampDiscriminator(Map<String, AttributeValue> sink, DynamoDbPersistentEntity<?> entity) {
		String column = entity.getDiscriminatorColumn();
		if (column.isEmpty()) {
			return;
		}
		sink.put(column, AttributeValue.builder().s(entity.getTypeName()).build());
	}

	public <R> R read(Map<String, AttributeValue> source, DynamoDbPersistentEntity<R> entity) {
		EntityInstantiator instantiator = this.instantiators.getInstantiatorFor(entity);
		InstanceCreatorMetadata<DynamoDbPersistentProperty> creatorMetadata = entity.getInstanceCreatorMetadata();

		ParameterValueProvider<DynamoDbPersistentProperty> provider = creatorMetadata != null
				&& creatorMetadata.hasParameters()
						? new PersistentEntityParameterValueProvider<>(entity,
								new DynamoDbPropertyValueProvider(source, entity), null)
						: NoOpParameterValueProvider.INSTANCE;
		R instance = instantiator.createInstance(entity, provider);
		ConvertingPropertyAccessor<R> propertyAccessor = newConvertingPropertyAccessor(instance, entity);

		for (DynamoDbPersistentProperty property : entity) {
			if (creatorMetadata != null && creatorMetadata.isCreatorParameter(property)) {
				continue;
			}
			if (propertyValueConversions.hasValueConverter(property)) {
				propertyAccessor.setProperty(property,
						readWithConverter(property, source.get(property.getColumnName())));
			}
			else if (property.isSpecialType() && !property.serializeAsJson()) {
				propertyAccessor.setProperty(property, readInnerClass(property, source, entity));
			}
			else {
				Class<?> elementType = null;
				if (property.isCollectionLike() && property.getTypeInformation().getComponentType() != null) {
					elementType = property.getTypeInformation().getComponentType().getType();
				}
				propertyAccessor.setProperty(property, fromAttributeValue(source.get(property.getColumnName()),
						property.getType(), elementType, property.serializeAsJson()));
			}
		}
		readSortKeyTemplates(source, instance, entity);
		return instance;
	}

	private Object resolvePropertyValue(DynamoDbPersistentProperty property, Map<String, AttributeValue> source,
			DynamoDbPersistentEntity<?> owner) {
		if (propertyValueConversions.hasValueConverter(property)) {
			return readWithConverter(property, source.get(property.getColumnName()));
		}
		if (property.isSpecialType() && !property.serializeAsJson()) {
			return readInnerClass(property, source, owner);
		}
		Class<?> elementType = null;
		if (property.isCollectionLike() && property.getTypeInformation().getComponentType() != null) {
			elementType = property.getTypeInformation().getComponentType().getType();
		}
		return fromAttributeValue(source.get(property.getColumnName()), property.getType(), elementType,
				property.serializeAsJson());
	}

	private final class DynamoDbPropertyValueProvider implements PropertyValueProvider<DynamoDbPersistentProperty> {

		private final Map<String, AttributeValue> source;
		private final DynamoDbPersistentEntity<?> entity;

		DynamoDbPropertyValueProvider(Map<String, AttributeValue> source, DynamoDbPersistentEntity<?> entity) {
			this.source = source;
			this.entity = entity;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> T getPropertyValue(DynamoDbPersistentProperty property) {
			return (T) resolvePropertyValue(property, this.source, this.entity);
		}
	}

	private <R> void readSortKeyTemplates(Map<String, AttributeValue> source, R instance,
			DynamoDbPersistentEntity<R> entity) {
		KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

		for (String column : resolver.templateIndexes()) {
			AttributeValue composedValue = source.get(resolver.columnFor(column));
			if (composedValue == null || composedValue.s() == null) {
				continue;
			}
			resolver.decomposeOnto(column, composedValue.s(), instance, getConversionService());
		}
	}

	private <S> ConvertingPropertyAccessor<S> newConvertingPropertyAccessor(S source,
			DynamoDbPersistentEntity<?> entity) {

		PersistentPropertyAccessor<S> propertyAccessor = source instanceof PersistentPropertyAccessor
				? (PersistentPropertyAccessor<S>) source
				: entity.getPropertyAccessor(source);

		return new ConvertingPropertyAccessor<>(propertyAccessor, getConversionService());
	}

	@Override
	public void write(Object source, Map<String, AttributeValue> sink) {
		Assert.notNull(source, "Value must not be null");

		Class<?> beanClassLoaderClass = transformClassToBeanClassLoaderClass(source.getClass());

		DynamoDbPersistentEntity<?> entity = getMappingContext().getRequiredPersistentEntity(beanClassLoaderClass);
		write(source, sink, entity);
	}

	private void fetchKeysAndPopulate(Object toBeUsed, Map<String, AttributeValue> keys,
			DynamoDbPersistentEntity<?> entity) {
		DynamoDbPersistentProperty persistentProperty = entity.getIdProperty();
		ConvertingPropertyAccessor convertingPropertyAccessor = newConvertingPropertyAccessor(toBeUsed, entity);
		keys.put(persistentProperty.getColumnName(),
				toAttributeValue(convertingPropertyAccessor.getProperty(persistentProperty), false));

		DynamoDbPersistentProperty rangeKey = entity.getKeySchema().singleSortKey();
		if (rangeKey != null) {
			keys.put(rangeKey.getColumnName(),
					toAttributeValue(convertingPropertyAccessor.getProperty(rangeKey), false));
		}
	}

	@Override
	public AttributeValue convertToDynamoDbType(Object obj, DynamoDbPersistentEntity<?> entity) {
		return toAttributeValue(obj, false);
	}

	@Override
	public MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> getMappingContext() {
		return this.mappingContext;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		if (getMappingContext() instanceof DynamoDbMappingContext dynamoDbMappingContext) {
			dynamoDbMappingContext.setApplicationContext(applicationContext);
		}
	}

	private <T> Class<T> transformClassToBeanClassLoaderClass(Class<T> entity) {

		try {
			return (Class<T>) ClassUtils.forName(entity.getName(), this.beanClassLoader);
		}
		catch (ClassNotFoundException | LinkageError ignore) {
			return entity;
		}
	}

	public AttributeValue toAttributeValue(@Nullable Object value, boolean serializeAsJson) {
		if (value == null) {
			return AttributeValue.builder().nul(Boolean.TRUE).build();
		}
		else if (value instanceof byte[] bytes) {
			return AttributeValue.builder().b(software.amazon.awssdk.core.SdkBytes.fromByteArray(bytes)).build();
		}
		else if (value instanceof Enum<?> anEnum) {
			return AttributeValue.builder().s(anEnum.name()).build();
		}
		else if (value instanceof Set<?> set) {
			if (set.isEmpty()) {
				return AttributeValue.builder().l(new ArrayList<>()).build();
			}
			List<AttributeValue> converted = new ArrayList<>(set.size());
			boolean allStrings = true;
			boolean allNumbers = true;
			boolean allBinary = true;
			for (Object element : set) {
				AttributeValue av = toAttributeValue(element, false);
				converted.add(av);
				if (av.s() == null) {
					allStrings = false;
				}
				if (av.n() == null) {
					allNumbers = false;
				}
				if (av.b() == null) {
					allBinary = false;
				}
			}
			if (allStrings) {
				List<String> asStrings = new ArrayList<>(converted.size());
				for (AttributeValue av : converted) {
					asStrings.add(av.s());
				}
				return AttributeValue.builder().ss(asStrings).build();
			}
			if (allNumbers) {
				List<String> asNumbers = new ArrayList<>(converted.size());
				for (AttributeValue av : converted) {
					asNumbers.add(av.n());
				}
				return AttributeValue.builder().ns(asNumbers).build();
			}
			if (allBinary) {
				List<software.amazon.awssdk.core.SdkBytes> asBinary = new ArrayList<>(converted.size());
				for (AttributeValue av : converted) {
					asBinary.add(av.b());
				}
				return AttributeValue.builder().bs(asBinary).build();
			}
			return AttributeValue.builder().l(converted).build();
		}
		else if (value instanceof List<?> in) {
			List<AttributeValue> out = new ArrayList<>();
			for (Object v : in) {
				out.add(toAttributeValue(v, false));
			}
			return AttributeValue.builder().l(out).build();
		}
		else if (value instanceof Map<?, ?> in) {
			Map<String, AttributeValue> attrs = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : in.entrySet()) {
				attrs.put(String.valueOf(e.getKey()), toAttributeValue(e.getValue(), false));
			}
			return AttributeValue.builder().m(attrs).build();
		}
		else if (serializeAsJson) {
			return toNativeStructure(value);
		}
		else if (conversionService.canConvert(value.getClass(), AttributeValue.class)) {
			return conversionService.convert(value, AttributeValue.class);
		}
		else {
			return toNativeStructure(value);
		}
	}

	private AttributeValue toNativeStructure(Object value) {
		Field[] fields = value.getClass().getDeclaredFields();
		if (fields.length == 0) {
			return AttributeValue.builder().s(String.valueOf(value)).build();
		}
		Map<String, AttributeValue> map = new LinkedHashMap<>();
		for (Field field : fields) {
			if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
				continue;
			}
			field.setAccessible(true);
			try {
				Object fieldValue = field.get(value);
				map.put(field.getName(), toAttributeValue(fieldValue, false));
			}
			catch (IllegalAccessException e) {
				throw new MappingException("Cannot read field '" + field.getName() + "' of " + value.getClass()
						+ " for serializeAsJson-style native structural encoding", e);
			}
		}
		return AttributeValue.builder().m(map).build();
	}

	private Object fromAttributeValue(@Nullable AttributeValue attributeValue, Class<?> type, boolean serializeAsJson) {
		return fromAttributeValue(attributeValue, type, null, serializeAsJson);
	}

	private Object fromAttributeValue(@Nullable AttributeValue attributeValue, Class<?> type,
			@Nullable Class<?> elementType, boolean serializeAsJson) {
		if (attributeValue == null || attributeValue.nul() != null) {
			return null;
		}
		else if (Enum.class.isAssignableFrom(type)) {
			if (attributeValue.s() == null) {
				throw new MappingException("Cannot read enum value of type " + type.getName()
						+ " from AttributeValue that does not carry a String (S) value");
			}
			return Enum.valueOf((Class<Enum>) type, attributeValue.s());
		}
		else if (List.class.isAssignableFrom(type)) {
			if (attributeValue.hasL()) {
				if (elementType != null) {
					return attributeValue.l().stream().map(av -> convertElementToType(av, elementType))
							.collect(Collectors.toList());
				}
				return attributeValue.l().stream().map(value -> convert(value, type)).collect(Collectors.toList());
			}
			else if (attributeValue.hasNs()) {
				if (elementType != null) {
					return attributeValue.ns().stream().map(s -> convertStringToType(s, elementType))
							.collect(Collectors.toList());
				}
				return attributeValue.ns();
			}
			else if (attributeValue.hasSs()) {
				if (elementType != null) {
					return attributeValue.ss().stream().map(s -> convertStringToType(s, elementType))
							.collect(Collectors.toList());
				}
				return attributeValue.ss();
			}
			return null;
		}
		else if (Set.class.isAssignableFrom(type)) {
			if (attributeValue.hasSs()) {
				if (elementType != null) {
					return attributeValue.ss().stream().map(s -> convertStringToType(s, elementType))
							.collect(Collectors.toCollection(LinkedHashSet::new));
				}
				return new LinkedHashSet<>(attributeValue.ss());
			}
			else if (attributeValue.hasNs()) {
				if (elementType != null) {
					return attributeValue.ns().stream().map(s -> convertStringToType(s, elementType))
							.collect(Collectors.toCollection(LinkedHashSet::new));
				}
				return new LinkedHashSet<>(attributeValue.ns());
			}
			else if (attributeValue.hasBs()) {
				LinkedHashSet<byte[]> out = new LinkedHashSet<>(attributeValue.bs().size());
				for (software.amazon.awssdk.core.SdkBytes bytes : attributeValue.bs()) {
					out.add(bytes.asByteArray());
				}
				return out;
			}
			else if (attributeValue.hasL()) {
				if (elementType != null) {
					return attributeValue.l().stream().map(av -> convertElementToType(av, elementType))
							.collect(Collectors.toCollection(LinkedHashSet::new));
				}
				return attributeValue.l().stream().map(this::convertPrimitiveType)
						.collect(Collectors.toCollection(LinkedHashSet::new));
			}
			return null;
		}
		else if (Map.class.isAssignableFrom(type)) {
			if (!attributeValue.hasM()) {
				return null;
			}
			Map<String, Object> map;
			if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
				map = new LinkedHashMap<>();
			}
			else {
				try {
					@SuppressWarnings("unchecked")
					Map<String, Object> instantiated = (Map<String, Object>) type.getDeclaredConstructor()
							.newInstance();
					map = instantiated;
				}
				catch (ReflectiveOperationException e) {
					throw new MappingException("Cannot instantiate " + type.getName()
							+ " for Map deserialization; the concrete Map type must expose a public no-arg constructor "
							+ "(or declare the field as java.util.Map / LinkedHashMap / HashMap)", e);
				}
			}
			attributeValue.m().forEach((key, value) -> {
				if (value.hasL() || value.hasNs()) {
					map.put(key, fromAttributeValue(value, List.class, serializeAsJson));
				}
				else if (value.hasSs() || value.hasBs()) {
					map.put(key, fromAttributeValue(value, Set.class, serializeAsJson));
				}
				else if (value.hasM()) {
					map.put(key, fromAttributeValue(value, Map.class, serializeAsJson));
				}
				else if (value.nul() != null) {
					map.put(key, null);
				}
				else {
					map.put(key, convertPrimitiveType(value));
				}
			});
			return map;
		}
		else if (conversionService.canConvert(AttributeValue.class, type)) {
			return this.conversionService.convert(attributeValue, type);
		}
		else if (serializeAsJson && attributeValue.hasM()) {
			return fromNativeStructure(attributeValue.m(), type);
		}
		else if (serializeAsJson && attributeValue.s() != null) {
			return attributeValue.s();
		}
		return null;
	}

	private Object fromNativeStructure(Map<String, AttributeValue> source, Class<?> type) {
		try {
			Constructor<?> constructor = type.getDeclaredConstructor();
			constructor.setAccessible(true);
			Object instance = constructor.newInstance();
			for (Field field : type.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
					continue;
				}
				AttributeValue fieldValue = source.get(field.getName());
				if (fieldValue == null) {
					continue;
				}
				field.setAccessible(true);
				field.set(instance, fromAttributeValue(fieldValue, field.getType(), false));
			}
			return instance;
		}
		catch (ReflectiveOperationException e) {
			throw new MappingException(
					"Cannot reconstruct " + type
							+ " from its native structural encoding (serializeAsJson) -- requires a no-arg constructor",
					e);
		}
	}

	public Object convert(AttributeValue attributeValue, Class type) {
		try {
			return convertPrimitiveType(attributeValue);
		}
		catch (UnsupportedOperationException e) {
			return attributeValue;
		}
	}

	private Object convertElementToType(AttributeValue av, Class<?> elementType) {
		if (elementType == null) {
			return convertPrimitiveType(av);
		}
		if (conversionService.canConvert(AttributeValue.class, elementType)) {
			return conversionService.convert(av, elementType);
		}
		Object primitive = convertPrimitiveType(av);
		return convertStringToType(String.valueOf(primitive), elementType);
	}

	private Object convertStringToType(String value, Class<?> elementType) {
		if (elementType == null || elementType == String.class) {
			return value;
		}
		if (Number.class.isAssignableFrom(elementType)) {
			return org.springframework.util.NumberUtils.parseNumber(value, (Class<? extends Number>) elementType);
		}
		if (conversionService.canConvert(String.class, elementType)) {
			return conversionService.convert(value, elementType);
		}
		return value;
	}

	@Override
	public Object convertPrimitiveType(AttributeValue value) {
		if (value.n() != null) {
			return value.n();
		}
		else if (value.s() != null) {
			return value.s();
		}
		else if (value.bool() != null) {
			return value.bool();
		}
		else if (value.b() != null) {
			return value.b().asByteArray();
		}
		else if (value.hasNs()) {
			return value.ns();
		}
		else if (value.hasSs()) {
			return value.ss();
		}
		else if (value.hasBs()) {
			return value.bs();
		}
		throw new UnsupportedOperationException("Could not get Correct Value");
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	enum NoOpParameterValueProvider implements ParameterValueProvider<DynamoDbPersistentProperty> {

		INSTANCE;

		@Override
		public <T> T getParameterValue(Parameter<T, DynamoDbPersistentProperty> parameter) {
			return null;
		}
	}
}
