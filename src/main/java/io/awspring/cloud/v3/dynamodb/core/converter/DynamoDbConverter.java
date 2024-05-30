package io.awspring.cloud.v3.dynamodb.core.converter;

import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbPersistenceEntity;
import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbPersistentProperty;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.convert.EntityConverter;
import org.springframework.data.convert.EntityReader;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;

import java.util.Map;

public interface DynamoDbConverter extends EntityConverter<DynamoDbPersistenceEntity<?>, DynamoDbPersistentProperty, Object,  Map<String, AttributeValue>>,
	DynamoDbWriter<Object>, EntityReader<Object, Map<String, AttributeValue>> {

	CustomConversions getCustomConversions();

	@Nullable
	Object getId(Object object, DynamoDbPersistenceEntity<?> entity);

    void write(Object objectToInsert, Map<String, AttributeValue> items, DynamoDbPersistenceEntity<?> persistentEntity);

	void delete(Object objectToDelete, Map<String, AttributeValue> object, DynamoDbPersistenceEntity<?> persistenceEntity);

	void findByKey(Object key, Map<String, AttributeValue> keys, DynamoDbPersistenceEntity<?> persistenceEntity);

	void findByKeys(String partitionKey, String sortKey, Map<String, AttributeValue> keys, DynamoDbPersistenceEntity<?> persistenceEntity);

	void update(Object objectToUpdate, Map<String, AttributeValue> keys, DynamoDbPersistenceEntity<?> entity, Map<String, AttributeValueUpdate> values);
}
