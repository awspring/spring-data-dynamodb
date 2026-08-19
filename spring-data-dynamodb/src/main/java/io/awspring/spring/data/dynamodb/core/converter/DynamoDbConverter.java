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
package io.awspring.spring.data.dynamodb.core.converter;

import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentProperty;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.convert.EntityConverter;
import org.springframework.data.convert.EntityReader;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public interface DynamoDbConverter extends
		EntityConverter<DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty, Object, Map<String, AttributeValue>>,
		DynamoDbWriter<Object>, EntityReader<Object, Map<String, AttributeValue>> {

	CustomConversions getCustomConversions();

	@Nullable
	Object getId(Object object, DynamoDbPersistentEntity<?> entity);

	void write(Object objectToInsert, Map<String, AttributeValue> items, DynamoDbPersistentEntity<?> persistentEntity);

	void writeKeyFromEntity(Object entity, Map<String, AttributeValue> keys,
			DynamoDbPersistentEntity<?> persistentEntity);

	void writeKey(Object partitionKey, Map<String, AttributeValue> keys, DynamoDbPersistentEntity<?> persistentEntity);

	void writeKey(Object partitionKey, @Nullable Object sortKey, Map<String, AttributeValue> keys,
			DynamoDbPersistentEntity<?> persistentEntity);

	void update(Object objectToUpdate, Map<String, AttributeValue> keys, DynamoDbPersistentEntity<?> entity,
			Map<String, AttributeValueUpdate> values);

	void stampDiscriminator(Map<String, AttributeValue> sink, DynamoDbPersistentEntity<?> entity);
}
