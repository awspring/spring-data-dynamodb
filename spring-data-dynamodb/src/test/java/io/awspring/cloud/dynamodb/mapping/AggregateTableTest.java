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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.AggregateItem;
import io.awspring.cloud.dynamodb.core.mapping.AggregateTable;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class AggregateTableTest {

	private static final String ORDER_PK = "ORDER#9876";
	private static final String ORDER_SK = "ORDER#9876";
	private static final String LINE_A_SK = "ORDER#9876#LINE#a";
	private static final String LINE_B_SK = "ORDER#9876#LINE#b";
	private static final String STATUS_PLACED = "PLACED";
	private static final String SKU_WIDGET_1 = "WIDGET-1";
	private static final String SKU_WIDGET_2 = "WIDGET-2";
	private static final String GSI1PK_VALUE = "PT#1";
	private static final String GSI1SK_VALUE = "ORDER#9876";

	private DynamoDbMappingContext mappingContext;
	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		this.mappingContext = new DynamoDbMappingContext();
		this.converter = new MappingDynamoDbConverter(mappingContext);
		this.converter.afterPropertiesSet();
	}

	private Map<String, AttributeValue> item(String pk, String sk, Map<String, String> rest) {
		Map<String, AttributeValue> item = new HashMap<>();
		item.put("pk", AttributeValue.builder().s(pk).build());
		item.put("sk", AttributeValue.builder().s(sk).build());
		rest.forEach((k, v) -> item.put(k, AttributeValue.builder().s(v).build()));
		return item;
	}

	@SuppressWarnings("unchecked")
	private <T> DynamoDbPersistentEntity<T> requiredEntity(Class<T> type) {
		return (DynamoDbPersistentEntity<T>) mappingContext.getRequiredPersistentEntity(type);
	}

	private static void assertBootstrapMessageContains(MappingException exception, String fragment) {
		StringBuilder messages = new StringBuilder();
		for (Throwable current = exception; current != null; current = current.getCause()) {
			messages.append(current.getMessage()).append('\n');
			for (Throwable suppressed : current.getSuppressed()) {
				messages.append(suppressed.getMessage()).append('\n');
			}
		}
		assertTrue(messages.toString().contains(fragment),
				() -> "expected a rejection mentioning \"" + fragment + "\" but got:\n" + messages);
	}

	@Nested
	@DisplayName("ReadAggregate")
	class ReadAggregate {

		@Test
		@DisplayName("root and line items fold into a typed aggregate")
		void readAggregate_withRootAndLines_foldsIntoAggregate() {
			List<Map<String, AttributeValue>> items = new ArrayList<>();
			items.add(item(ORDER_PK, ORDER_SK, Map.of("status", STATUS_PLACED)));
			items.add(item(ORDER_PK, LINE_A_SK, Map.of("sku", SKU_WIDGET_1)));
			items.add(item(ORDER_PK, LINE_B_SK, Map.of("sku", SKU_WIDGET_2)));

			DynamoDbPersistentEntity<Order> entity = requiredEntity(Order.class);
			Order order = converter.readAggregate(items, entity);

			assertAll(() -> assertEquals(STATUS_PLACED, order.order.status), () -> assertEquals(2, order.lines.size()),
					() -> assertEquals(SKU_WIDGET_1, order.lines.get(0).sku),
					() -> assertEquals(SKU_WIDGET_2, order.lines.get(1).sku));
		}

		@Test
		@DisplayName("root member with no match resolves to null")
		void readAggregate_noRootMatch_rootIsNull() {
			List<Map<String, AttributeValue>> items = new ArrayList<>();
			items.add(item(ORDER_PK, LINE_A_SK, Map.of("sku", SKU_WIDGET_1)));

			DynamoDbPersistentEntity<Order> entity = requiredEntity(Order.class);
			Order order = converter.readAggregate(items, entity);

			assertAll(() -> assertNull(order.order), () -> assertEquals(1, order.lines.size()));
		}

		@Test
		@DisplayName("second match on a single-valued member is rejected")
		void readAggregate_duplicateRoot_throws() {
			List<Map<String, AttributeValue>> items = new ArrayList<>();
			items.add(item(ORDER_PK, ORDER_SK, Map.of("status", STATUS_PLACED)));
			items.add(item(ORDER_PK, "ORDER#1234", Map.of("status", STATUS_PLACED)));

			DynamoDbPersistentEntity<Order> entity = requiredEntity(Order.class);

			assertThrows(IllegalStateException.class, () -> converter.readAggregate(items, entity));
		}
	}

	@Nested
	@DisplayName("BootstrapValidation")
	class BootstrapValidation {

		@Test
		@DisplayName("blank table name is rejected")
		void bootstrap_blankTableName_throws() {
			MappingException exception = assertThrows(MappingException.class,
					() -> new DynamoDbMappingContext().getRequiredPersistentEntity(BlankTableName.class));

			assertBootstrapMessageContains(exception, "tableName() must not be blank");
		}

		@Test
		@DisplayName("no @AggregateItem members is rejected")
		void bootstrap_noChildren_throws() {
			MappingException exception = assertThrows(MappingException.class,
					() -> new DynamoDbMappingContext().getRequiredPersistentEntity(NoChildren.class));

			assertBootstrapMessageContains(exception, "must declare at least one @AggregateItem member");
		}

		@Test
		@DisplayName("child without a routing pattern is rejected")
		void bootstrap_unroutedChild_throws() {
			MappingException exception = assertThrows(MappingException.class,
					() -> new DynamoDbMappingContext().getRequiredPersistentEntity(UnroutedChildren.class));

			assertBootstrapMessageContains(exception, "declares none of startsWith/endsWith/regex");
		}

		@Test
		@DisplayName("base-table aggregate with blank sort key is rejected")
		void bootstrap_baseTableBlankSortKey_throws() {
			MappingException exception = assertThrows(MappingException.class,
					() -> new DynamoDbMappingContext().getRequiredPersistentEntity(BaseTableBlankSortKey.class));

			assertBootstrapMessageContains(exception, "sortKey() must not be blank for a base-table aggregate");
		}
	}

	@Nested
	@DisplayName("GsiScopedAggregate")
	class GsiScopedAggregate {

		@Test
		@DisplayName("GSI aggregate with per-member sort keys bootstraps cleanly")
		void bootstrap_gsiWithMemberSortKeys_succeeds() {
			DynamoDbPersistentEntity<GsiScopedWithMemberSortKeys> entity = requiredEntity(
					GsiScopedWithMemberSortKeys.class);

			assertAll(() -> assertTrue(entity.isAggregateView()),
					() -> assertEquals("GSI1", entity.getAggregateIndexName()),
					() -> assertEquals("", entity.getAggregateSortKeyColumn()));
		}

		@Test
		@DisplayName("GSI aggregate with blank sort key and member missing its own sort key is rejected")
		void bootstrap_gsiMissingMemberSortKey_throws() {
			MappingException exception = assertThrows(MappingException.class, () -> new DynamoDbMappingContext()
					.getRequiredPersistentEntity(GsiScopedMissingMemberSortKey.class));

			assertBootstrapMessageContains(exception, "must declare its own @AggregateItem.sortKey()");
		}

		@Test
		@DisplayName("GSI aggregate folds using per-member sort key column")
		void readAggregate_gsiPerMemberSortKey_foldsCorrectly() {
			List<Map<String, AttributeValue>> items = new ArrayList<>();
			Map<String, AttributeValue> orderItem = new HashMap<>();
			orderItem.put("gsi1pk", AttributeValue.builder().s(GSI1PK_VALUE).build());
			orderItem.put("gsi1sk", AttributeValue.builder().s(GSI1SK_VALUE).build());
			orderItem.put("status", AttributeValue.builder().s(STATUS_PLACED).build());
			items.add(orderItem);

			DynamoDbPersistentEntity<GsiScopedWithMemberSortKeys> entity = requiredEntity(
					GsiScopedWithMemberSortKeys.class);
			GsiScopedWithMemberSortKeys aggregate = converter.readAggregate(items, entity);

			assertEquals(STATUS_PLACED, aggregate.order.status);
		}
	}

	// --- Test fixtures ---

	@Table(tableName = "commerce")
	public static class OrderRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String status;

		public OrderRow() {
		}
	}

	@Table(tableName = "commerce")
	public static class LineRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String sku;

		public LineRow() {
		}
	}

	@AggregateTable(tableName = "commerce", partitionKey = "pk", sortKey = "sk")
	public static class Order {
		@AggregateItem(regex = "ORDER#[^#]+")
		OrderRow order;
		@AggregateItem(regex = "ORDER#[^#]+#LINE#[^#]+")
		List<LineRow> lines;

		public Order() {
		}
	}

	@AggregateTable(tableName = "", partitionKey = "pk", sortKey = "sk")
	static class BlankTableName {
		@AggregateItem(regex = "X")
		OrderRow order;

		public BlankTableName() {
		}
	}

	@AggregateTable(tableName = "commerce", partitionKey = "pk", sortKey = "sk")
	static class NoChildren {
		String notAChildMember;

		public NoChildren() {
		}
	}

	@AggregateTable(tableName = "commerce", partitionKey = "pk", sortKey = "sk")
	static class UnroutedChildren {
		@AggregateItem
		OrderRow order;

		public UnroutedChildren() {
		}
	}

	@AggregateTable(tableName = "commerce", partitionKey = "pk")
	static class BaseTableBlankSortKey {
		@AggregateItem(regex = "ORDER#[^#]+")
		OrderRow order;

		public BaseTableBlankSortKey() {
		}
	}

	@AggregateTable(tableName = "commerce", partitionKey = "gsi1pk", indexName = "GSI1")
	static class GsiScopedWithMemberSortKeys {
		@AggregateItem(regex = "ORDER#[^#]+", sortKey = "gsi1sk")
		OrderRow order;

		public GsiScopedWithMemberSortKeys() {
		}
	}

	@AggregateTable(tableName = "commerce", partitionKey = "gsi1pk", indexName = "GSI1")
	static class GsiScopedMissingMemberSortKey {
		@AggregateItem(regex = "ORDER#[^#]+", sortKey = "gsi1sk")
		OrderRow order;
		@AggregateItem(regex = "ITEM#[^#]+")
		List<LineRow> lines;

		public GsiScopedMissingMemberSortKey() {
		}
	}

	static class PlainPojo {
		String value;

		public PlainPojo() {
		}
	}

	@AggregateTable(tableName = "commerce", partitionKey = "pk", sortKey = "sk")
	static class NonTableChild {
		@AggregateItem(regex = "X")
		PlainPojo notATableEntity;

		public NonTableChild() {
		}
	}
}
