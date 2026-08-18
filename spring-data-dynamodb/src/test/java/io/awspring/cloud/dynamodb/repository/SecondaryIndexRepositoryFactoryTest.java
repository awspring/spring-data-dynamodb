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
package io.awspring.cloud.dynamodb.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.awspring.cloud.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.support.DynamoDbEntityInformation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.context.MappingContext;

@DisplayName("SecondaryIndexRepositoryFactory")
class SecondaryIndexRepositoryFactoryTest {

	private static final String TABLE_NAME = "orders";

	static class MatchesByRoundView {
		String round;
		String createdAt;
	}

	interface MatchesByRoundRepository extends SecondaryIndexRepository<MatchesByRoundView> {
	}

	@Table(tableName = TABLE_NAME)
	static class Match {
		@PartitionKey
		String id;
		@SortKey
		String matchId;
		String round;
	}

	interface MatchRepository extends DynamoDbRepository<Match, String> {
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static <M extends DynamoDbPersistentEntity<?>> void stubMappingContext(DynamoDbConverter converter,
			MappingContext<M, DynamoDbPersistentProperty> mappingContext) {
		when(converter.getMappingContext()).thenReturn((MappingContext) mappingContext);
	}

	private DynamoDbRepositoryFactory factory() {
		DynamoDbOperations operations = mock(DynamoDbOperations.class);
		DynamoDbConverter converter = mock(DynamoDbConverter.class);
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		when(operations.getConverter()).thenReturn(converter);
		stubMappingContext(converter, mappingContext);
		return new DynamoDbRepositoryFactory(operations);
	}

	@Nested
	@DisplayName("SecondaryIndexRepository (view domain type without id)")
	class SecondaryIndexViewRepositoryTests {

		@Test
		@DisplayName("bootstraps without throwing despite no id property on the view type")
		void factoryBuildsSecondaryIndexViewRepositoryWithoutIdPropertyError() {
			// Arrange
			DynamoDbRepositoryFactory factory = factory();

			// Act
			MatchesByRoundRepository repository = Assertions.assertDoesNotThrow(
					() -> factory.getRepository(MatchesByRoundRepository.class),
					"A SecondaryIndexRepository-derived interface must bootstrap despite its view domain "
							+ "type having no base-table id property");

			// Assert
			assertNotNull(repository);
		}

		@Test
		@DisplayName("entity information reports no id attribute and Void id type")
		void viewEntityInformationHasNoIdAndReportsVoidIdType() {
			// Arrange
			DynamoDbRepositoryFactory factory = factory();

			// Act
			DynamoDbEntityInformation<MatchesByRoundView, Object> info = factory
					.getEntityInformation(MatchesByRoundView.class, false);

			// Assert
			Assertions.assertAll(() -> assertNull(info.getIdAttribute(), "A view domain type has no id attribute"),
					() -> assertEquals(Void.class, info.getIdType(), "An id-less view reports Void as its id type"),
					() -> assertNotNull(info.getTableName()));
		}
	}

	@Nested
	@DisplayName("Normal DynamoDbRepository (base-table entity with id)")
	class NormalRepositoryTests {

		@Test
		@DisplayName("bootstraps and reports id attribute and type correctly")
		void normalRepositoryStillBuildsAndReportsIdAttribute() {
			// Arrange
			DynamoDbRepositoryFactory factory = factory();

			// Act
			MatchRepository repository = Assertions.assertDoesNotThrow(
					() -> factory.getRepository(MatchRepository.class), "A normal DynamoDbRepository must still build");

			// Assert
			assertNotNull(repository);

			DynamoDbEntityInformation<Match, Object> info = factory.getEntityInformation(Match.class, false);
			Assertions.assertAll(
					() -> assertEquals("id", info.getIdAttribute(),
							"A base-table entity still reports its id attribute"),
					() -> assertEquals(String.class, info.getIdType(),
							"A base-table entity still reports its scalar id type"));
		}
	}
}
