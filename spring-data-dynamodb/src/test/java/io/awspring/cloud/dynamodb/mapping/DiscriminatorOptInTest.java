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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class DiscriminatorOptInTest {

	@Table(tableName = "orders")
	static class PlainOrder {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String round;
	}

	@Table(tableName = "arena", discriminator = "_type", typeName = "MATCH")
	static class MatchRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
	}

	@Table(tableName = "arena", discriminator = "_type", typeName = "PLAYER")
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

	@Test
	void anEntityWithNoDiscriminatorHasAnEmptyColumnName() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		DynamoDbPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(PlainOrder.class);

		assertEquals("", entity.getDiscriminatorColumn());
	}

	@Test
	void writingAnEntityWithNoDiscriminatorStampsNoExtraColumn() {
		MappingDynamoDbConverter converter = converterFor(PlainOrder.class);
		DynamoDbPersistentEntity<?> entity = converter.getMappingContext()
				.getRequiredPersistentEntity(PlainOrder.class);

		PlainOrder match = new PlainOrder();
		match.pk = "cust-1";
		match.sk = "MATCH#1";
		match.round = "QUARTERFINAL";

		Map<String, AttributeValue> item = new LinkedHashMap<>();
		converter.write(match, item, entity);
		converter.stampDiscriminator(item, entity);

		assertEquals(3, item.size());
		assertFalse(item.containsKey("_type"));
	}

	@Test
	void writingAnEntityWithADiscriminatorStampsItsTypeName() {
		MappingDynamoDbConverter converter = converterFor(MatchRow.class, PlayerRow.class);
		DynamoDbPersistentEntity<?> entity = converter.getMappingContext().getRequiredPersistentEntity(MatchRow.class);

		MatchRow row = new MatchRow();
		row.pk = "TOURNAMENT#1";
		row.sk = "MATCH#m1";

		Map<String, AttributeValue> item = new LinkedHashMap<>();
		converter.write(row, item, entity);
		converter.stampDiscriminator(item, entity);

		assertTrue(item.containsKey("_type"));
		assertEquals("MATCH", item.get("_type").s());
	}

	@Test
	void classLessReadResolvesTheOptedInTypeFromTheDiscriminatorColumn() {
		MappingDynamoDbConverter converter = converterFor(MatchRow.class, PlayerRow.class);
		DynamoDbPersistentEntity<?> entity = converter.getMappingContext().getRequiredPersistentEntity(MatchRow.class);

		MatchRow row = new MatchRow();
		row.pk = "TOURNAMENT#1";
		row.sk = "MATCH#m1";

		Map<String, AttributeValue> item = new LinkedHashMap<>();
		converter.write(row, item, entity);
		converter.stampDiscriminator(item, entity);

		Object resolved = converter.read(item);
		assertTrue(resolved instanceof MatchRow);
	}

	@Test
	void classLessReadFailsFastWhenNoEntityOnTheTableOptedIn() {
		MappingDynamoDbConverter converter = converterFor(PlainOrder.class);
		DynamoDbPersistentEntity<?> entity = converter.getMappingContext()
				.getRequiredPersistentEntity(PlainOrder.class);

		PlainOrder match = new PlainOrder();
		match.pk = "cust-1";
		match.sk = "MATCH#1";
		match.round = "QUARTERFINAL";

		Map<String, AttributeValue> item = new LinkedHashMap<>();
		converter.write(match, item, entity);
		converter.stampDiscriminator(item, entity);

		assertThrows(MappingException.class, () -> converter.read(item));
	}

	@Test
	void typeDiscriminatorRegistryRequiresAtLeastOneOptedInEntity() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		DynamoDbPersistentEntity<?> plain = mappingContext.getRequiredPersistentEntity(PlainOrder.class);

		assertThrows(MappingException.class, () -> TypeDiscriminatorRegistry.fromEntities(java.util.List.of(plain)));
	}

	@Test
	void typeDiscriminatorRegistryResolvesFromTheOptedInColumn() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		DynamoDbPersistentEntity<?> match = mappingContext.getRequiredPersistentEntity(MatchRow.class);
		DynamoDbPersistentEntity<?> player = mappingContext.getRequiredPersistentEntity(PlayerRow.class);

		TypeDiscriminatorRegistry registry = TypeDiscriminatorRegistry.fromEntities(java.util.List.of(match, player));

		assertEquals("_type", registry.discriminatorColumn());
		assertEquals(MatchRow.class, registry.resolve("MATCH"));
		assertEquals(PlayerRow.class, registry.resolve("PLAYER"));
	}
}
