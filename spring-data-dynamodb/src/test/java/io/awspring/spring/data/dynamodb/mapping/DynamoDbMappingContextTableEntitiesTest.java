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
package io.awspring.spring.data.dynamodb.mapping;

import static org.junit.jupiter.api.Assertions.*;

import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import java.util.Collection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DynamoDbMappingContextTableEntitiesTest {

	private static final String SHARED_TABLE_NAME = "shared_table";
	private static final String DEDICATED_TABLE_NAME = "dedicated_table";
	private static final String UNKNOWN_TABLE_NAME = "no_such_table";

	@Table(tableName = "shared_table")
	static class Tournament {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
	}

	@Table(tableName = "shared_table")
	static class Match {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
	}

	@Table(tableName = "dedicated_table")
	static class SoloEntity {
		@PartitionKey
		String pk;
	}

	@Nested
	@DisplayName("Shared table")
	class SharedTable {

		@Test
		@DisplayName("Returns all entities registered for a shared table")
		void getEntitiesForTable_sharedTable_returnsAll() {
			DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
			mappingContext.getRequiredPersistentEntity(Tournament.class);
			mappingContext.getRequiredPersistentEntity(Match.class);

			Collection<DynamoDbPersistentEntity<?>> entities = mappingContext.getEntitiesForTable(SHARED_TABLE_NAME);

			assertAll("shared table entities", () -> assertEquals(2, entities.size()),
					() -> assertTrue(entities.stream().anyMatch(e -> e.getType().equals(Tournament.class))),
					() -> assertTrue(entities.stream().anyMatch(e -> e.getType().equals(Match.class))));
		}
	}

	@Nested
	@DisplayName("Dedicated table")
	class DedicatedTable {

		@Test
		@DisplayName("Returns single entity for a dedicated table")
		void getEntitiesForTable_dedicatedTable_returnsSingleEntity() {
			DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
			mappingContext.getRequiredPersistentEntity(SoloEntity.class);

			Collection<DynamoDbPersistentEntity<?>> entities = mappingContext.getEntitiesForTable(DEDICATED_TABLE_NAME);

			assertEquals(1, entities.size());
		}
	}

	@Nested
	@DisplayName("Unknown table")
	class UnknownTable {

		@Test
		@DisplayName("Returns empty collection for an unknown table rather than null")
		void getEntitiesForTable_unknownTable_returnsEmptyCollection() {
			DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();

			Collection<DynamoDbPersistentEntity<?>> entities = mappingContext.getEntitiesForTable(UNKNOWN_TABLE_NAME);

			assertAll("unknown table result", () -> assertNotNull(entities), () -> assertTrue(entities.isEmpty()));
		}
	}
}
