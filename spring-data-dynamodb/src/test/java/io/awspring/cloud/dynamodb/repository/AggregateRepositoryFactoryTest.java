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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.awspring.cloud.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.AggregateItem;
import io.awspring.cloud.dynamodb.core.mapping.AggregateTable;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.support.DynamoDbEntityInformation;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.context.MappingContext;

@DisplayName("AggregateRepositoryFactory")
class AggregateRepositoryFactoryTest {

	private static final String TABLE_NAME = "commerce";

	@Table(tableName = TABLE_NAME)
	static class OrderRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String status;
	}

	@Table(tableName = TABLE_NAME)
	static class LineRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String sku;
	}

	@AggregateTable(tableName = TABLE_NAME, partitionKey = "pk", sortKey = "sk")
	static class OrderAggregate {
		@AggregateItem(regex = "ORDER#[^#]+")
		OrderRow order;
		@AggregateItem(regex = "ORDER#[^#]+#LINE#[^#]+")
		List<LineRow> lines;
	}

	interface OrderAggregateRepository extends AggregateRepository<OrderAggregate> {
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
	@DisplayName("AggregateRepository bootstrap")
	class BootstrapTests {

		@Test
		@DisplayName("bootstraps without throwing despite no id property on the aggregate type")
		void factoryBuildsAggregateRepositoryWithoutIdPropertyError() {
			// Arrange
			DynamoDbRepositoryFactory factory = factory();

			// Act
			OrderAggregateRepository repository = Assertions.assertDoesNotThrow(
					() -> factory.getRepository(OrderAggregateRepository.class),
					"An AggregateRepository-derived interface must bootstrap despite its aggregate domain "
							+ "type having no base-table id property");

			// Assert
			assertNotNull(repository);
		}
	}

	@Nested
	@DisplayName("Aggregate entity information")
	class EntityInformationTests {

		@Test
		@DisplayName("reports no id attribute, Void id type, and correct table name")
		void aggregateEntityInformationHasNoIdAndReportsVoidIdType() {
			// Arrange
			DynamoDbRepositoryFactory factory = factory();

			// Act
			DynamoDbEntityInformation<OrderAggregate, Object> info = factory.getEntityInformation(OrderAggregate.class,
					false);

			// Assert
			assertAll(() -> assertNull(info.getIdAttribute(), "An aggregate domain type has no id attribute"),
					() -> assertEquals(Void.class, info.getIdType(),
							"An id-less aggregate reports Void as its id type"),
					() -> assertEquals(TABLE_NAME, info.getTableName()));
		}
	}
}
