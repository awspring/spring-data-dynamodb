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

import static org.junit.jupiter.api.Assertions.assertAll;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.convert.PropertyValueConversions;
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.convert.ValueConverter;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class PropertyValueConverterTest {

	private static final String TABLE_NAME = "secretTable";
	private static final String ID_1 = "id-1";
	private static final String ID_2 = "id-2";
	private static final String CODE_VALUE = "hello";
	private static final String CODE_REVERSED = "olleh";
	private static final String PLAIN_VALUE = "world";
	private static final String PLAYER_ID = "testID";

	private DynamoDbMappingContext mappingContext;
	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		this.mappingContext = new DynamoDbMappingContext();
		this.converter = new MappingDynamoDbConverter(mappingContext);
		this.converter.afterPropertiesSet();
	}

	@Nested
	@DisplayName("With PropertyValueConversions enabled")
	class WithPropertyValueConversions {

		@BeforeEach
		void enableConversions() {
			converter.setPropertyValueConversions(PropertyValueConversions.simple(cfg -> {
			}));
		}

		@Test
		@DisplayName("write – with value converter – stores converted value")
		void write_withValueConverter_storesConvertedValue() {
			SecretEntity entity = new SecretEntity(ID_1, CODE_VALUE, PLAIN_VALUE);

			Map<String, AttributeValue> item = new HashMap<>();
			converter.write(entity, item);

			assertAll(
					() -> assertEquals(CODE_REVERSED, item.get("code").s(),
							"code must be stored through the reversing converter"),
					() -> assertEquals(PLAIN_VALUE, item.get("plain").s()));
		}

		@Test
		@DisplayName("read – with value converter – reverses back to original")
		void read_withValueConverter_reversesBackToOriginal() {
			SecretEntity entity = new SecretEntity(ID_1, CODE_VALUE, PLAIN_VALUE);
			Map<String, AttributeValue> item = new HashMap<>();
			converter.write(entity, item);

			SecretEntity read = converter.read(SecretEntity.class, item);

			assertAll(
					() -> assertEquals(CODE_VALUE, read.getCode(),
							"code must be reversed back on read -> original value"),
					() -> assertEquals(PLAIN_VALUE, read.getPlain()), () -> assertEquals(ID_1, read.getId()));
		}
	}

	@Nested
	@DisplayName("Without PropertyValueConversions")
	class WithoutPropertyValueConversions {

		@Test
		@DisplayName("write – without converter – stores raw value")
		void write_withoutConverter_storesRawValue() {
			SecretEntity entity = new SecretEntity(ID_2, CODE_VALUE, PLAIN_VALUE);

			Map<String, AttributeValue> item = new HashMap<>();
			converter.write(entity, item);

			assertAll(() -> assertEquals(CODE_VALUE, item.get("code").s()),
					() -> assertNotEquals(CODE_REVERSED, item.get("code").s()),
					() -> assertEquals(PLAIN_VALUE, item.get("plain").s()));
		}

		@Test
		@DisplayName("read – without converter – returns raw value")
		void read_withoutConverter_returnsRawValue() {
			SecretEntity entity = new SecretEntity(ID_2, CODE_VALUE, PLAIN_VALUE);
			Map<String, AttributeValue> item = new HashMap<>();
			converter.write(entity, item);

			SecretEntity read = converter.read(SecretEntity.class, item);

			assertAll(() -> assertEquals(CODE_VALUE, read.getCode()), () -> assertEquals(PLAIN_VALUE, read.getPlain()));
		}
	}

	@Nested
	@DisplayName("Pre-existing entity round-trip")
	class PreExistingEntityRoundTrip {

		@Test
		@DisplayName("write – PlayerCardEntity – preserves all fields")
		void write_playerCardEntity_preservesAllFields() {
			LocalDate testDate = LocalDate.now();
			PlayerCardEntity playerCard = new PlayerCardEntity(PLAYER_ID, testDate, Arrays.asList("test1", "test2"),
					Collections.singletonList("099"));

			Map<String, AttributeValue> item = new HashMap<>();
			converter.write(playerCard, item);

			assertAll(() -> assertEquals(PLAYER_ID, item.get("id").s()),
					() -> assertEquals(testDate.toString(), item.get("registeredOn").s()),
					() -> assertEquals(2, item.get("tags").l().size()));
		}
	}

	@Table(tableName = TABLE_NAME)
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
