package io.awspring.cloud.v3.dynamodb.core.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.v3.dynamodb.core.mapping.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.mapping.*;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.ConvertingPropertyAccessor;
import org.springframework.data.mapping.model.EntityInstantiator;
import org.springframework.data.mapping.model.ParameterValueProvider;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;

import java.util.*;
import java.util.stream.Collectors;

public class MappingDynamoDbConverter extends AbstractDynamoDbConverter implements ApplicationContextAware, BeanClassLoaderAware {
    private @Nullable
    ClassLoader beanClassLoader;

    //There should be option to change mapper in a future.
    private final ObjectMapper objectMapper;
    private final DynamoDbMappingContext mappingContext;


    public MappingDynamoDbConverter(DynamoDbMappingContext mappingContext, ObjectMapper objectMapper) {
        super(newConversionService());
        DynamoDbConversions conversions = new DynamoDbConversions(Collections.emptyList());
        this.mappingContext = mappingContext;
        this.setCustomConversions(conversions);
        this.objectMapper = objectMapper;
    }

    private static ConversionService newConversionService() {
        return new DefaultConversionService();
    }

    @Override
    public Object getId(Object object, DynamoDbPersistenceEntity<?> entity) {

        Assert.notNull(object, "Object instance must not be null");
        Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");

        ConvertingPropertyAccessor<?> propertyAccessor = newConvertingPropertyAccessor(object, entity);

        DynamoDbPersistentProperty idProperty = entity.getIdProperty();

        Assert.notNull(idProperty, "ID property cannot be null.");
        return propertyAccessor.getProperty(idProperty);
    }


    @Override
    public void write(Object obj, Map<String, AttributeValue> items, DynamoDbPersistenceEntity<?> persistentEntity) {

        if (obj == null) {
            return;
        }

        if (persistentEntity == null) {
            throw new MappingException("No mapping metadata found for " + persistentEntity.getClass());
        }

        ConvertingPropertyAccessor convertingPropertyAccessor = newConvertingPropertyAccessor(obj, persistentEntity);
        for (DynamoDbPersistentProperty property : persistentEntity) {
            if (property.isSpecialType() &&  !property.serializeAsJson()) {
                Class<?> beanClassLoaderClass = transformClassToBeanClassLoaderClass(property.getTypeOfProperty());
                DynamoDbPersistenceEntity<?> entity = getMappingContext().getRequiredPersistentEntity(beanClassLoaderClass);
                write(convertingPropertyAccessor.getProperty(property), items, entity);
            } else {
                writeInternal(convertingPropertyAccessor, property, items);
            }
        }
    }

    @Nullable
    private void writeInternal(ConvertingPropertyAccessor<?> convertingPropertyAccessor, DynamoDbPersistentProperty property, Map<String, AttributeValue> attributeValueMap) {
        attributeValueMap.put(property.getColumnName(), toAttributeValue(convertingPropertyAccessor.getProperty(property), property.serializeAsJson()));
    }

    @Nullable
    private void writeInternalUpdate(ConvertingPropertyAccessor<?> convertingPropertyAccessor, DynamoDbPersistentProperty property, Map<String, AttributeValueUpdate> attributeValueMap) {
        attributeValueMap.put(property.getColumnName(), AttributeValueUpdate.builder().value(toAttributeValue(convertingPropertyAccessor.getProperty(property), property.serializeAsJson())).build());
    }
    @Override
    public void delete(Object objectToDelete, Map<String, AttributeValue> keys, DynamoDbPersistenceEntity<?> persistenceEntity) {
        fetchKeysAndPopulate(objectToDelete, keys, persistenceEntity);
    }

    @Override
    public void findByKey(Object key, Map<String, AttributeValue> keys, DynamoDbPersistenceEntity<?> persistenceEntity) {
        DynamoDbPersistentProperty persistentProperty = persistenceEntity.getPersistentProperty(PartitionKey.class);
        keys.put(persistentProperty.getColumnName(), toAttributeValue(key, false));
    }

    @Override
    public void findByKeys(String partitionKey, String sortKey, Map<String, AttributeValue> keys, DynamoDbPersistenceEntity<?> persistenceEntity) {
        DynamoDbPersistentProperty persistentProperty = persistenceEntity.getPersistentProperty(PartitionKey.class);
        keys.put(persistentProperty.getColumnName(), toAttributeValue(partitionKey, false));
        if (sortKey != null) {
            persistentProperty = persistenceEntity.getPersistentProperty(SortKey.class);
            keys.put(persistentProperty.getColumnName(), toAttributeValue(sortKey, false));
        }
    }

    @Override
    public void update(Object objectToUpdate, Map<String, AttributeValue> keys, DynamoDbPersistenceEntity<?> entity, Map<String, AttributeValueUpdate> values) {
        fetchKeysAndPopulate(objectToUpdate, keys, entity);
        updateRecoursive(objectToUpdate, values, entity);
    }
    public void updateRecoursive(Object objectToUpdate, Map<String, AttributeValueUpdate> values, DynamoDbPersistenceEntity<?> entity) {
        for (DynamoDbPersistentProperty property : entity) {
            if (!property.isIdProperty() && !property.isRangeKey() && property.isSpecialType()) {
                Class<?> beanClassLoaderClass = transformClassToBeanClassLoaderClass(property.getTypeOfProperty());
                DynamoDbPersistenceEntity<?> persis = getMappingContext().getRequiredPersistentEntity(beanClassLoaderClass);
                updateRecoursive(newConvertingPropertyAccessor(objectToUpdate, entity).getProperty(property), values, persis);
            }  else if (!property.isIdProperty() && !property.isRangeKey()) {
                writeInternalUpdate(newConvertingPropertyAccessor(objectToUpdate, entity), property, values);
            }
        }

    }


    @Override
    public <R> R read(Class<R> type, Map<String, AttributeValue> source) {
        DynamoDbPersistenceEntity<R> entity = (DynamoDbPersistenceEntity<R>) getMappingContext()
                .getPersistentEntity(type);
		/*
		If we gonna call constructor instead of mapping without.
		PreferredConstructor<R, DynamoDbPersistentProperty> persistenceConstructor = entity.getPersistenceConstructor();
		 */
        DynamoDbPersistentProperty rangeKey = null;
        for (DynamoDbPersistentProperty persistentProperty : entity) {
            if (persistentProperty.isRangeKey()) {
                rangeKey = persistentProperty;
            }
        }


       return read(source, rangeKey, false, entity);
    }

    public <R> R read(Map<String, AttributeValue> source, DynamoDbPersistentProperty rangeKey, boolean flag, DynamoDbPersistenceEntity<R> entity) {
		/*
		If we gonna call constructor instead of mapping without.
		PreferredConstructor<R, DynamoDbPersistentProperty> persistenceConstructor = entity.getPersistenceConstructor();
		 */
        EntityInstantiator instantiator = this.instantiators.getInstantiatorFor(entity);
        ParameterValueProvider<DynamoDbPersistentProperty> provider = NoOpParameterValueProvider.INSTANCE;
        R instance = instantiator.createInstance(entity, provider);
        ConvertingPropertyAccessor<R> propertyAccessor = newConvertingPropertyAccessor(instance, entity);

        for (DynamoDbPersistentProperty property : entity) {
            if (property.isSpecialType() && !property.serializeAsJson()) {
                if (rangeKey != null && property.sortKeyRegex() != null && rangeKey.getType().isAssignableFrom(String.class) && (source.get(rangeKey.getColumnName()).s().startsWith(property.sortKeyRegex()))) {
                    Class<?> beanClassLoaderClass = transformClassToBeanClassLoaderClass(property.getTypeOfProperty());
                    DynamoDbPersistenceEntity<?> newEntity = getMappingContext().getRequiredPersistentEntity(beanClassLoaderClass);
                    propertyAccessor.setProperty(property, read(source, rangeKey, flag, newEntity));
                    flag = true;
                } else if (rangeKey == null || property.sortKeyRegex() == null  && !flag) {
                    Class<?> beanClassLoaderClass = transformClassToBeanClassLoaderClass(property.getTypeOfProperty());
                    DynamoDbPersistenceEntity<?> newEntity = getMappingContext().getRequiredPersistentEntity(beanClassLoaderClass);
                    propertyAccessor.setProperty(property, read(source, rangeKey, flag, newEntity));
                }
            } else {
                propertyAccessor.setProperty(property, fromAttributeValue(source.get(property.getColumnName()), property.getType(), property.serializeAsJson()));
            }
        }
        return instance;
    }



    private <S> ConvertingPropertyAccessor<S> newConvertingPropertyAccessor(S source,
                                                                            DynamoDbPersistenceEntity<?> entity) {

        PersistentPropertyAccessor<S> propertyAccessor = source instanceof PersistentPropertyAccessor
                ? (PersistentPropertyAccessor<S>) source
                : entity.getPropertyAccessor(source);

        return new ConvertingPropertyAccessor<>(propertyAccessor, getConversionService());
    }

    @Override
    public void write(Object source, Map<String, AttributeValue> sink) {
        Assert.notNull(source, "Value must not be null");

        Class<?> beanClassLoaderClass = transformClassToBeanClassLoaderClass(source.getClass());

        DynamoDbPersistenceEntity<?> entity = getMappingContext().getRequiredPersistentEntity(beanClassLoaderClass);
        write(source, sink, entity);
    }

    private void fetchKeysAndPopulate(Object toBeUsed, Map<String, AttributeValue> keys, DynamoDbPersistenceEntity<?> entity) {
        DynamoDbPersistentProperty persistentProperty = entity.getPersistentProperty(PartitionKey.class);
        ConvertingPropertyAccessor convertingPropertyAccessor = newConvertingPropertyAccessor(toBeUsed, entity);
        keys.put(persistentProperty.getColumnName(), toAttributeValue(convertingPropertyAccessor.getProperty(persistentProperty), false));

        Iterable<DynamoDbPersistentProperty> persistentProperties = entity.getPersistentProperties(SortKey.class);
        persistentProperties.forEach(rangeProperty -> {
            keys.put(rangeProperty.getColumnName(), toAttributeValue(convertingPropertyAccessor.getProperty(rangeProperty), false));
        });
    }

    @Override
    public AttributeValue convertToDynamoDbType(Object obj, DynamoDbPersistenceEntity<?> entity) {
        return toAttributeValue(obj, false);
    }

    @Override
    public MappingContext<? extends DynamoDbPersistenceEntity<?>, DynamoDbPersistentProperty> getMappingContext() {
        return this.mappingContext;
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

    }

    private <T> Class<T> transformClassToBeanClassLoaderClass(Class<T> entity) {

        try {
            return (Class<T>) ClassUtils.forName(entity.getName(), this.beanClassLoader);
        } catch (ClassNotFoundException | LinkageError ignore) {
            return entity;
        }
    }


    public AttributeValue toAttributeValue(Object value, boolean serializeAsJson) {
        if (value == null) {
            return AttributeValue.builder().nul(Boolean.TRUE).build();
        } else if (value instanceof Set) {
            Set<Object> set = (Set<Object>) value;
            if (set.size() == 0) {
                return AttributeValue.builder().ss(new ArrayList<>()).build();
            }
            Object element = set.iterator().next();
            return conversionService.convert(element, AttributeValue.class);
        } else if (value instanceof List) {
            List<Object> in = (List<Object>) value;
            List<AttributeValue> out = new ArrayList<AttributeValue>();
            for (Object v : in) {
                out.add(toAttributeValue(v, false));
            }
            return AttributeValue.builder().l(out).build();
        } else if (value instanceof Map) {
            Map<String, Object> in = (Map<String, Object>) value;
            Map<String, AttributeValue> attrs = new HashMap<>();
            for (Map.Entry<String, Object> e : in.entrySet()) {
                attrs.put(e.getKey(), toAttributeValue(e.getValue(), false));
            }
            return AttributeValue.builder().m(attrs).build();
        } else if (serializeAsJson) {
            try {
                return AttributeValue.builder().s(objectMapper.writeValueAsString(value)).build();
            } catch (JsonProcessingException e) {
                throw new UnsupportedOperationException("Cannot convert value: " + value, e);
            }
        }
        else if (conversionService.canConvert(value.getClass(), AttributeValue.class)) {
            return conversionService.convert(value, AttributeValue.class);
        } else {
            try {
                return AttributeValue.builder().s(objectMapper.writeValueAsString(value)).build();
            } catch (JsonProcessingException e) {
                throw new UnsupportedOperationException("Cannot convert value: " + value, e);
            }
        }
    }

    private Object fromAttributeValue(AttributeValue attributeValue, Class<?> type, boolean serializeAsJson) {
        if (List.class.isAssignableFrom(type)) {
            if (attributeValue.hasL()) {
                return attributeValue.l().stream().map(value -> convert(value, type)).collect(Collectors.toList());
            } else if (attributeValue.hasNs()) {
                return attributeValue.ns();
            }
            return null;
        } else if (Set.class.isAssignableFrom(type)) {
            if (attributeValue.hasSs()) {
                return attributeValue.ss().stream().map(value -> conversionService.convert(value, type)).collect(Collectors.toSet());
            }
            return null;
        } else if (Map.class.isAssignableFrom(type)) {
            attributeValue.m().forEach((key, value) -> {

            });
        } else if (serializeAsJson) {
            try {
                return objectMapper.readValue(attributeValue.s(), type);
            } catch (JsonProcessingException e) {
                throw new UnsupportedOperationException("Cannot convert value: " + attributeValue, e);
            }
        }
        else if (conversionService.canConvert(AttributeValue.class, type)) {
            return this.conversionService.convert(attributeValue, type);
        }
        return null;
    }

    public Object convert (AttributeValue attributeValue, Class type) {
        try {
            return getCorrectValue(attributeValue);
        } catch (UnsupportedOperationException e) {
            return objectMapper.convertValue(attributeValue, type);
        }
    }


    private Object getCorrectValue(AttributeValue value) {
        if (value.n() != null) {
            return value.n();
        } else if (value.s() != null) {
            return value.s();
        } else if (value.bool() != null) {
            return value.bool();
        } else if (value.b() != null) {
            return value.b().asByteArray();
        } else if (value.hasNs()) {
            return value.ns();
        } else if (value.hasSs()) {
            return value.ss();
        } else if (value.hasBs()) {
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
