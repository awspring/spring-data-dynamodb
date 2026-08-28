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
package io.awspring.spring.data.dynamodb.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.awspring.spring.data.dynamodb.core.DynamoDbOperations;
import io.awspring.spring.data.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.spring.data.dynamodb.core.mapping.ItemCollectionMember;
import io.awspring.spring.data.dynamodb.core.mapping.ItemCollectionView;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.repository.support.DynamoDbEntityInformation;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.context.MappingContext;

@DisplayName("ItemCollectionRepositoryFactory")
class ItemCollectionRepositoryFactoryTest {

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

	@ItemCollectionView(tableName = TABLE_NAME, partitionKey = "pk", sortKey = "sk")
	static class OrderItemCollection {
		@ItemCollectionMember(regex = "ORDER#[^#]+")
		OrderRow order;
		@ItemCollectionMember(regex = "ORDER#[^#]+#LINE#[^#]+")
		List<LineRow> lines;
	}

	interface OrderItemCollectionRepository extends ItemCollectionRepository<OrderItemCollection> {
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
	@DisplayName("ItemCollectionRepository bootstrap")
	class BootstrapTests {

		@Test
		@DisplayName("bootstraps without throwing despite no id property on the view type")
		void factoryBuildsItemCollectionRepositoryWithoutIdPropertyError() {
			// Arrange
			DynamoDbRepositoryFactory factory = factory();

			// Act
			OrderItemCollectionRepository repository = Assertions.assertDoesNotThrow(
					() -> factory.getRepository(OrderItemCollectionRepository.class),
					"An ItemCollectionRepository-derived interface must bootstrap despite its view domain "
							+ "type having no base-table id property");

			// Assert
			assertNotNull(repository);
		}
	}

	@Nested
	@DisplayName("ItemCollection entity information")
	class EntityInformationTests {

		@Test
		@DisplayName("reports no id attribute, Void id type, and correct table name")
		void viewEntityInformationHasNoIdAndReportsVoidIdType() {
			// Arrange
			DynamoDbRepositoryFactory factory = factory();

			// Act
			DynamoDbEntityInformation<OrderItemCollection, Object> info = factory
					.getEntityInformation(OrderItemCollection.class, false);

			// Assert
			assertAll(() -> assertNull(info.getIdAttribute(), "An view domain type has no id attribute"),
					() -> assertEquals(Void.class, info.getIdType(), "An id-less view reports Void as its id type"),
					() -> assertEquals(TABLE_NAME, info.getTableName()));
		}
	}
}
