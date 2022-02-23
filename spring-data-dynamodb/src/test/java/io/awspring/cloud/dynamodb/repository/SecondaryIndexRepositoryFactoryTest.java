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
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.context.MappingContext;

public class SecondaryIndexRepositoryFactoryTest {

	static class MatchesByRoundView {
		String round;
		String createdAt;
	}

	interface MatchesByRoundRepository extends SecondaryIndexRepository<MatchesByRoundView> {
	}

	@Table(tableName = "orders")
	static class Match {
		@PartitionKey
		String id;
		@SortKey
		String matchId;
		String round;
	}

	interface MatchRepository extends DynamoDbRepository<Match, String> {
	}

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

	@Test
	void factoryBuildsSecondaryIndexViewRepositoryWithoutIdPropertyError() {
		DynamoDbRepositoryFactory factory = factory();

		MatchesByRoundRepository repository = Assertions.assertDoesNotThrow(
				() -> factory.getRepository(MatchesByRoundRepository.class),
				"A SecondaryIndexRepository-derived interface must bootstrap despite its view domain "
						+ "type having no base-table id property");

		assertNotNull(repository);
	}

	@Test
	void viewEntityInformationHasNoIdAndReportsVoidIdType() {
		DynamoDbRepositoryFactory factory = factory();

		DynamoDbEntityInformation<MatchesByRoundView, Object> info = factory
				.getEntityInformation(MatchesByRoundView.class);

		assertNull(info.getIdAttribute(), "A view domain type has no id attribute");
		assertEquals(Void.class, info.getIdType(), "An id-less view reports Void as its id type");
		assertNotNull(info.getTableName());
	}

	@Test
	void normalRepositoryStillBuildsAndReportsIdAttribute() {
		DynamoDbRepositoryFactory factory = factory();

		MatchRepository repository = Assertions.assertDoesNotThrow(() -> factory.getRepository(MatchRepository.class),
				"A normal DynamoDbRepository must still build");
		assertNotNull(repository);

		DynamoDbEntityInformation<Match, Object> info = factory.getEntityInformation(Match.class);
		assertEquals("id", info.getIdAttribute(), "A base-table entity still reports its id attribute");
		assertEquals(String.class, info.getIdType(), "A base-table entity still reports its scalar id type");
	}
}
