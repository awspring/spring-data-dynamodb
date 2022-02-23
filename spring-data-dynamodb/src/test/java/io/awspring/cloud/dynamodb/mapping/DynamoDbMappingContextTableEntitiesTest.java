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

import static org.junit.jupiter.api.Assertions.*;

import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.util.Collection;
import org.junit.jupiter.api.Test;

public class DynamoDbMappingContextTableEntitiesTest {

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

	@Test
	void returnsAllEntitiesRegisteredForASharedTable() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Tournament.class);
		mappingContext.getRequiredPersistentEntity(Match.class);

		Collection<DynamoDbPersistentEntity<?>> entities = mappingContext.getEntitiesForTable("shared_table");

		assertEquals(2, entities.size());
		assertTrue(entities.stream().anyMatch(e -> e.getType().equals(Tournament.class)));
		assertTrue(entities.stream().anyMatch(e -> e.getType().equals(Match.class)));
	}

	@Test
	void returnsSingleEntityForADedicatedTable() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(SoloEntity.class);

		Collection<DynamoDbPersistentEntity<?>> entities = mappingContext.getEntitiesForTable("dedicated_table");

		assertEquals(1, entities.size());
	}

	@Test
	void returnsEmptyCollectionForAnUnknownTableRatherThanNull() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();

		Collection<DynamoDbPersistentEntity<?>> entities = mappingContext.getEntitiesForTable("no_such_table");

		assertNotNull(entities);
		assertTrue(entities.isEmpty());
	}
}
