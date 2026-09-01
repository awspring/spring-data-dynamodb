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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@DisplayName("MappingDynamoDbConverter -- read/write validation and enum handling")
class MappingDynamoDbConverterValidationTest {

	private DynamoDbMappingContext mappingContext;
	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		this.mappingContext = new DynamoDbMappingContext();
		this.converter = new MappingDynamoDbConverter(mappingContext);
		this.converter.afterPropertiesSet();
	}

	@Test
	void classAwareReadWithNullSourceFailsFast() {
		assertThrows(IllegalArgumentException.class,
				() -> converter.read(Player.class, (Map<String, AttributeValue>) null));
	}

	@Test
	void classAwareReadWithNullTypeFailsFast() {
		assertThrows(IllegalArgumentException.class, () -> converter.read((Class<Object>) null, new HashMap<>()));
	}

	@Test
	void classAwareReadReturnsNullForEmptySource() {
		assertNull(converter.read(Player.class, new HashMap<>()));
	}

	@Test
	void writeWithNullObjectFailsFast() {
		Map<String, AttributeValue> sink = new HashMap<>();
		assertThrows(IllegalArgumentException.class,
				() -> converter.write(null, sink, mappingContext.getRequiredPersistentEntity(Player.class)));
	}

	@Test
	void writeWithNullItemsFailsFast() {
		Player p = new Player();
		p.id = "x";
		assertThrows(IllegalArgumentException.class,
				() -> converter.write(p, null, mappingContext.getRequiredPersistentEntity(Player.class)));
	}

	@Test
	void enumFromNonStringAttributeValueFails() {
		Map<String, AttributeValue> item = new HashMap<>();
		item.put("id", AttributeValue.builder().s("e1").build());
		item.put("colour", AttributeValue.builder().n("42").build());

		assertThrows(MappingException.class, () -> converter.read(EntityWithEnum.class, item));
	}

	@Test
	void enumRoundTrips() {
		EntityWithEnum entity = new EntityWithEnum();
		entity.id = "e1";
		entity.colour = Colour.RED;

		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(entity, item);

		EntityWithEnum restored = converter.read(EntityWithEnum.class, item);
		assertNotNull(restored);
		assertEquals(Colour.RED, restored.colour);
	}

	@Table(tableName = "player")
	public static class Player {
		@PartitionKey
		public String id;

		public Player() {
		}
	}

	public enum Colour {
		RED, GREEN, BLUE
	}

	@Table(tableName = "enum_test")
	public static class EntityWithEnum {
		@PartitionKey
		public String id;
		public Colour colour;

		public EntityWithEnum() {
		}
	}
}
