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
package io.awspring.cloud.dynamodb.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.convert.PropertyValueConversions;
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.convert.ValueConverter;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class PropertyValueConverterTest {

	private DynamoDbMappingContext mappingContext;
	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		this.mappingContext = new DynamoDbMappingContext();
		this.converter = new MappingDynamoDbConverter(mappingContext);
		this.converter.afterPropertiesSet();
	}

	@Test
	void propertyWithConverterRoundTripsViaConverter() {
		converter.setPropertyValueConversions(PropertyValueConversions.simple(cfg -> {
		}));

		SecretEntity entity = new SecretEntity("id-1", "hello", "world");
		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(entity, item);

		assertEquals("olleh", item.get("code").s(), "code must be stored through the reversing converter");
		assertEquals("world", item.get("plain").s());

		SecretEntity read = converter.read(SecretEntity.class, item);
		assertEquals("hello", read.getCode(), "code must be reversed back on read -> original value");
		assertEquals("world", read.getPlain());
		assertEquals("id-1", read.getId());
	}

	@Test
	void propertyWithoutConverterUsesExistingPath() {
		SecretEntity entity = new SecretEntity("id-2", "hello", "world");
		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(entity, item);

		assertEquals("hello", item.get("code").s());
		assertNotEquals("olleh", item.get("code").s());
		assertEquals("world", item.get("plain").s());

		SecretEntity read = converter.read(SecretEntity.class, item);
		assertEquals("hello", read.getCode());
		assertEquals("world", read.getPlain());
	}

	@Test
	void preExistingRoundTripUnchanged() {
		LocalDate testDate = LocalDate.now();
		PlayerCardEntity playerCard = new PlayerCardEntity("testID", testDate, Arrays.asList("test1", "test2"),
				Collections.singletonList("099"));
		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(playerCard, item);

		assertEquals("testID", item.get("id").s());
		assertEquals(testDate.toString(), item.get("registeredOn").s());
		assertEquals(2, item.get("tags").l().size());
	}

	@Table(tableName = "secretTable")
	static class SecretEntity {

		@PartitionKey
		private String id;

		@ValueConverter(ReversingConverter.class)
		private String code;

		private String plain;

		public SecretEntity() {
		}

		public SecretEntity(String id, String code, String plain) {
			this.id = id;
			this.code = code;
			this.plain = plain;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getCode() {
			return code;
		}

		public void setCode(String code) {
			this.code = code;
		}

		public String getPlain() {
			return plain;
		}

		public void setPlain(String plain) {
			this.plain = plain;
		}
	}

	static class ReversingConverter implements
			PropertyValueConverter<String, AttributeValue, ValueConversionContext<DynamoDbPersistentProperty>> {

		@Override
		public String read(AttributeValue value, ValueConversionContext<DynamoDbPersistentProperty> context) {
			return new StringBuilder(value.s()).reverse().toString();
		}

		@Override
		public AttributeValue write(String value, ValueConversionContext<DynamoDbPersistentProperty> context) {
			return AttributeValue.builder().s(new StringBuilder(value).reverse().toString()).build();
		}
	}
}
