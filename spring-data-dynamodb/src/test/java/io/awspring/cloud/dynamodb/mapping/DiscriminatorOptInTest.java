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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.core.mapping.TypeDiscriminatorRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class DiscriminatorOptInTest {

	private static final String DISCRIMINATOR_COLUMN = "_type";
	private static final String TABLE_ORDERS = "orders";
	private static final String TABLE_ARENA = "arena";
	private static final String TYPE_MATCH = "MATCH";
	private static final String TYPE_PLAYER = "PLAYER";
	private static final String PK_CUSTOMER = "cust-1";
	private static final String SK_MATCH_1 = "MATCH#1";
	private static final String PK_TOURNAMENT = "TOURNAMENT#1";
	private static final String SK_MATCH_M1 = "MATCH#m1";
	private static final String ROUND_QUARTERFINAL = "QUARTERFINAL";

	@Table(tableName = TABLE_ORDERS)
	static class PlainOrder {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String round;
	}

	@Table(tableName = TABLE_ARENA, discriminator = DISCRIMINATOR_COLUMN, typeName = TYPE_MATCH)
	static class MatchRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
	}

	@Table(tableName = TABLE_ARENA, discriminator = DISCRIMINATOR_COLUMN, typeName = TYPE_PLAYER)
	static class PlayerRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
	}

	private MappingDynamoDbConverter converterFor(Class<?>... entityTypes) {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		for (Class<?> type : entityTypes) {
			mappingContext.getRequiredPersistentEntity(type);
		}
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		return converter;
	}

	@Nested
	@DisplayName("Write without discriminator")
	class WriteNoDiscriminator {

		@Test
		@DisplayName("Entity with no discriminator has an empty column name")
		void getDiscriminatorColumn_noDiscriminatorConfigured_returnsEmpty() {
			DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();

			DynamoDbPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(PlainOrder.class);

			assertEquals("", entity.getDiscriminatorColumn());
		}

		@Test
		@DisplayName("Writing an entity with no discriminator stamps no extra column")
		void write_noDiscriminator_doesNotStampTypeColumn() {
			MappingDynamoDbConverter converter = converterFor(PlainOrder.class);
			DynamoDbPersistentEntity<?> entity = converter.getMappingContext()
					.getRequiredPersistentEntity(PlainOrder.class);
			PlainOrder order = new PlainOrder();
			order.pk = PK_CUSTOMER;
			order.sk = SK_MATCH_1;
			order.round = ROUND_QUARTERFINAL;

			Map<String, AttributeValue> item = new LinkedHashMap<>();
			converter.write(order, item, entity);
			converter.stampDiscriminator(item, entity);

			assertAll(() -> assertEquals(3, item.size()), () -> assertFalse(item.containsKey(DISCRIMINATOR_COLUMN)));
		}
	}

	@Nested
	@DisplayName("Write with discriminator")
	class WriteWithDiscriminator {

		@Test
		@DisplayName("Writing an entity with a discriminator stamps its type name")
		void write_withDiscriminator_stampsTypeName() {
			MappingDynamoDbConverter converter = converterFor(MatchRow.class, PlayerRow.class);
			DynamoDbPersistentEntity<?> entity = converter.getMappingContext()
					.getRequiredPersistentEntity(MatchRow.class);
			MatchRow row = new MatchRow();
			row.pk = PK_TOURNAMENT;
			row.sk = SK_MATCH_M1;

			Map<String, AttributeValue> item = new LinkedHashMap<>();
			converter.write(row, item, entity);
			converter.stampDiscriminator(item, entity);

			assertAll(() -> assertTrue(item.containsKey(DISCRIMINATOR_COLUMN)),
					() -> assertEquals(TYPE_MATCH, item.get(DISCRIMINATOR_COLUMN).s()));
		}
	}

	@Nested
	@DisplayName("Class-less read")
	class ClasslessRead {

		@Test
		@DisplayName("Resolves the opted-in type from the discriminator column")
		void read_withDiscriminatorColumn_resolvesCorrectType() {
			MappingDynamoDbConverter converter = converterFor(MatchRow.class, PlayerRow.class);
			DynamoDbPersistentEntity<?> entity = converter.getMappingContext()
					.getRequiredPersistentEntity(MatchRow.class);
			MatchRow row = new MatchRow();
			row.pk = PK_TOURNAMENT;
			row.sk = SK_MATCH_M1;
			Map<String, AttributeValue> item = new LinkedHashMap<>();
			converter.write(row, item, entity);
			converter.stampDiscriminator(item, entity);

			Object resolved = converter.read(item);

			assertInstanceOf(MatchRow.class, resolved);
		}

		@Test
		@DisplayName("Fails fast when no entity on the table opted in")
		void read_noEntityOptedIn_throwsMappingException() {
			MappingDynamoDbConverter converter = converterFor(PlainOrder.class);
			DynamoDbPersistentEntity<?> entity = converter.getMappingContext()
					.getRequiredPersistentEntity(PlainOrder.class);
			PlainOrder order = new PlainOrder();
			order.pk = PK_CUSTOMER;
			order.sk = SK_MATCH_1;
			order.round = ROUND_QUARTERFINAL;
			Map<String, AttributeValue> item = new LinkedHashMap<>();
			converter.write(order, item, entity);
			converter.stampDiscriminator(item, entity);

			assertThrows(MappingException.class, () -> converter.read(item));
		}
	}

	@Nested
	@DisplayName("TypeDiscriminatorRegistry")
	class Registry {

		@Test
		@DisplayName("Requires at least one opted-in entity")
		void fromEntities_noOptedInEntity_throwsMappingException() {
			DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
			DynamoDbPersistentEntity<?> plain = mappingContext.getRequiredPersistentEntity(PlainOrder.class);

			assertThrows(MappingException.class, () -> TypeDiscriminatorRegistry.fromEntities(List.of(plain)));
		}

		@Test
		@DisplayName("Resolves types from the opted-in discriminator column")
		void fromEntities_multipleOptedIn_resolvesCorrectly() {
			DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
			DynamoDbPersistentEntity<?> match = mappingContext.getRequiredPersistentEntity(MatchRow.class);
			DynamoDbPersistentEntity<?> player = mappingContext.getRequiredPersistentEntity(PlayerRow.class);

			TypeDiscriminatorRegistry registry = TypeDiscriminatorRegistry.fromEntities(List.of(match, player));

			assertAll(() -> assertEquals(DISCRIMINATOR_COLUMN, registry.discriminatorColumn()),
					() -> assertEquals(MatchRow.class, registry.resolve(TYPE_MATCH)),
					() -> assertEquals(PlayerRow.class, registry.resolve(TYPE_PLAYER)));
		}
	}
}
