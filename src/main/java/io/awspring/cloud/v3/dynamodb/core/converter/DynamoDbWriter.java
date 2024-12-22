package io.awspring.cloud.v3.dynamodb.core.converter;

import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbPersistenceEntity;
import org.springframework.data.convert.EntityWriter;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
interface DynamoDbWriter<T> extends EntityWriter<T, Map<String, AttributeValue>> {
	AttributeValue convertToDynamoDbType(@Nullable Object obj, DynamoDbPersistenceEntity<?> entity);

	Object convertPrimitiveType(AttributeValue value);
}
