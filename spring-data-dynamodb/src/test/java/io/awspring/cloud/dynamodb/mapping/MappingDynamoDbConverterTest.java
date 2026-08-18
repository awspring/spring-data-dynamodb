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

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.InnerClass;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.PatternSyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class MappingDynamoDbConverterTest {

	private static final String PK_CUSTOMER = "CUSTOMER#12345";
	private static final String SK_ORDER = "ORDER#9876";
	private static final String SK_ORDER_LINE = "ORDER#9876#LINE#abc";
	private static final String SK_ORDER_LINE_ITEM = "ORDER#9876#LINE#abc#ITEM#52526";
	private static final String ORDER_ID = "9876";
	private static final String SKU = "ABC123";
	private static final String SERIAL = "52526";

	private DynamoDbMappingContext mappingContext;
	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		this.mappingContext = new DynamoDbMappingContext();
		this.converter = new MappingDynamoDbConverter(mappingContext);
		this.converter.afterPropertiesSet();
	}

	@Nested
	@DisplayName("Basic write/read")
	class BasicWriteRead {

		@Test
		@DisplayName("Write PlayerCardEntity writes all fields correctly")
		void write_playerCardEntity_writesAllFields() {
			LocalDate testDate = LocalDate.now();
			PlayerCardEntity entity = new PlayerCardEntity("testID", testDate, Arrays.asList("test1", "test2"),
					Collections.singletonList("099"));

			Map<String, AttributeValue> item = new HashMap<>();
			converter.write(entity, item);

			assertAll("written PlayerCardEntity fields", () -> assertEquals("testID", item.get("id").s()),
					() -> assertEquals(testDate.toString(), item.get("registeredOn").s()),
					() -> assertEquals(2, item.get("tags").l().size()));
		}
	}

	@Nested
	@DisplayName("InnerClass chaining")
	class InnerClassChaining {

		@Test
		@DisplayName("Chained @InnerClass flattens all levels and reconstructs on read")
		void chainedInnerClasses_flattenAllLevels_reconstruct() {
			ChainLeaf leaf = new ChainLeaf();
			leaf.setLeafValue("deep");
			ChainMiddle middle = new ChainMiddle();
			middle.setMiddleName("mid");
			middle.setLeaf(leaf);
			ChainOuter outer = new ChainOuter();
			outer.setId("o1");
			outer.setMiddle(middle);

			Map<String, AttributeValue> item = new HashMap<>();
			converter.write(outer, item);

			assertAll("flattened item", () -> assertEquals("o1", item.get("id").s()),
					() -> assertEquals("mid", item.get("middleName").s()),
					() -> assertEquals("deep", item.get("leafValue").s()),
					() -> assertFalse(item.containsKey("middle")), () -> assertFalse(item.containsKey("leaf")));

			ChainOuter read = converter.read(ChainOuter.class, item);

			assertAll("reconstructed chain", () -> assertNotNull(read.getMiddle()),
					() -> assertEquals("mid", read.getMiddle().getMiddleName()),
					() -> assertNotNull(read.getMiddle().getLeaf()),
					() -> assertEquals("deep", read.getMiddle().getLeaf().getLeafValue()));
		}
	}

	@Nested
	@DisplayName("Regex routing")
	class RegexRouting {

		@Test
		@DisplayName("Regex separates hierarchical sort keys that prefix routing cannot")
		void regex_separatesHierarchicalSortKeys_exclusively() {
			Map<String, AttributeValue> orderItem = new HashMap<>();
			orderItem.put("pk", AttributeValue.builder().s(PK_CUSTOMER).build());
			orderItem.put("sk", AttributeValue.builder().s(SK_ORDER).build());
			orderItem.put("orderId", AttributeValue.builder().s(ORDER_ID).build());
			orderItem.put("orderStatus", AttributeValue.builder().s("OPEN").build());

			RegexRoutedRow order = converter.read(RegexRoutedRow.class, orderItem);

			assertAll("order-level row", () -> assertNotNull(order.getOrder()),
					() -> assertEquals("OPEN", order.getOrder().getOrderStatus()), () -> assertNull(order.getLine()));

			Map<String, AttributeValue> lineItem = new HashMap<>();
			lineItem.put("pk", AttributeValue.builder().s(PK_CUSTOMER).build());
			lineItem.put("sk", AttributeValue.builder().s(SK_ORDER_LINE).build());
			lineItem.put("orderId", AttributeValue.builder().s(ORDER_ID).build());
			lineItem.put("sku", AttributeValue.builder().s(SKU).build());

			RegexRoutedRow line = converter.read(RegexRoutedRow.class, lineItem);

			assertAll("line-level row", () -> assertNotNull(line.getLine()),
					() -> assertEquals(SKU, line.getLine().getSku()), () -> assertNull(line.getOrder()));
		}

		@Test
		@DisplayName("Regex must match the whole sort key, not a substring")
		void regex_mustMatchWholeSortKey_rejectsSubstringMatch() {
			Map<String, AttributeValue> item = new HashMap<>();
			item.put("pk", AttributeValue.builder().s(PK_CUSTOMER).build());
			item.put("sk", AttributeValue.builder().s("ARCHIVED#ORDER#9876").build());
			item.put("orderStatus", AttributeValue.builder().s("OPEN").build());

			RegexRoutedRow read = converter.read(RegexRoutedRow.class, item);

			assertAll("no member matched", () -> assertNull(read.getOrder()), () -> assertNull(read.getLine()));
		}

		@Test
		@DisplayName("Regex routes a deeper hierarchy exclusively")
		void regex_routesDeeperHierarchy_exclusively() {
			Map<String, AttributeValue> item = new HashMap<>();
			item.put("pk", AttributeValue.builder().s(PK_CUSTOMER).build());
			item.put("sk", AttributeValue.builder().s(SK_ORDER_LINE_ITEM).build());
			item.put("orderId", AttributeValue.builder().s(ORDER_ID).build());
			item.put("sku", AttributeValue.builder().s(SKU).build());
			item.put("serial", AttributeValue.builder().s(SERIAL).build());

			RegexRoutedRow read = converter.read(RegexRoutedRow.class, item);

			assertAll("deepest member only", () -> assertNotNull(read.getItem()),
					() -> assertEquals(SERIAL, read.getItem().getSerial()), () -> assertNull(read.getOrder()),
					() -> assertNull(read.getLine()));
		}

		@Test
		@DisplayName("A shallower row does not reach the deeper member")
		void shallowerRow_doesNotReachDeeperMember() {
			Map<String, AttributeValue> lineItem = new HashMap<>();
			lineItem.put("pk", AttributeValue.builder().s(PK_CUSTOMER).build());
			lineItem.put("sk", AttributeValue.builder().s(SK_ORDER_LINE).build());
			lineItem.put("sku", AttributeValue.builder().s(SKU).build());

			RegexRoutedRow read = converter.read(RegexRoutedRow.class, lineItem);

			assertAll("line present, item absent", () -> assertNotNull(read.getLine()),
					() -> assertNull(read.getItem()));
		}
	}

	@Nested
	@DisplayName("Prefix routing")
	class PrefixRouting {

		@Test
		@DisplayName("Prefix routing alone cannot separate hierarchical sort keys")
		void prefixRouting_cannotSeparateHierarchicalKeys() {
			Map<String, AttributeValue> lineItem = new HashMap<>();
			lineItem.put("pk", AttributeValue.builder().s(PK_CUSTOMER).build());
			lineItem.put("sk", AttributeValue.builder().s(SK_ORDER_LINE).build());
			lineItem.put("orderId", AttributeValue.builder().s(ORDER_ID).build());
			lineItem.put("sku", AttributeValue.builder().s(SKU).build());

			PrefixRoutedRow read = converter.read(PrefixRoutedRow.class, lineItem);

			assertAll("both members match the same prefix", () -> assertNotNull(read.getLine()),
					() -> assertNotNull(read.getOrder()));
		}
	}

	@Nested
	@DisplayName("Validation rejections")
	class ValidationRejections {

		@Test
		@DisplayName("Invalid regex is rejected when the entity is mapped")
		void invalidRegex_rejectedAtMapping() {
			MappingException exception = assertThrows(MappingException.class,
					() -> mappingContext.getRequiredPersistentEntity(BadRegexRow.class).getPersistentProperty("order"));

			Throwable cause = exception;
			while (cause != null && !cause.getMessage().contains("ORDER#[unclosed")) {
				cause = cause.getCause();
			}

			assertNotNull(cause, "expected the offending pattern somewhere in the cause chain of: " + exception);
			assertInstanceOf(PatternSyntaxException.class, cause.getCause());
		}
	}

	// --- Entity classes ---

	@Table(tableName = "single_table")
	public static class RegexRoutedRow {
		@PartitionKey
		private String pk;
		@SortKey
		private String sk;
		@InnerClass(regex = "ORDER#[^#]+")
		private OrderPart order;
		@InnerClass(regex = "ORDER#[^#]+#LINE#[^#]+")
		private OrderLinePart line;
		@InnerClass(regex = "ORDER#[^#]+#LINE#[^#]+#ITEM#[^#]+")
		private LineItemPart item;

		public LineItemPart getItem() {
			return item;
		}

		public void setItem(LineItemPart item) {
			this.item = item;
		}

		public OrderPart getOrder() {
			return order;
		}

		public void setOrder(OrderPart order) {
			this.order = order;
		}

		public OrderLinePart getLine() {
			return line;
		}

		public void setLine(OrderLinePart line) {
			this.line = line;
		}
	}

	@Table(tableName = "prefix_routed_table")
	public static class PrefixRoutedRow {
		@PartitionKey
		private String pk;
		@SortKey
		private String sk;
		@InnerClass(startsWith = "ORDER#")
		private OrderPart order;
		@InnerClass(startsWith = "ORDER#")
		private OrderLinePart line;

		public OrderPart getOrder() {
			return order;
		}

		public void setOrder(OrderPart order) {
			this.order = order;
		}

		public OrderLinePart getLine() {
			return line;
		}

		public void setLine(OrderLinePart line) {
			this.line = line;
		}
	}

	@Table(tableName = "bad_regex_table")
	public static class BadRegexRow {
		@PartitionKey
		private String pk;
		@SortKey
		private String sk;
		@InnerClass(regex = "ORDER#[unclosed")
		private OrderPart order;

		public OrderPart getOrder() {
			return order;
		}

		public void setOrder(OrderPart order) {
			this.order = order;
		}
	}

	@Table(tableName = "chain")
	public static class ChainOuter {
		@PartitionKey
		private String id;
		@InnerClass
		private ChainMiddle middle;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public ChainMiddle getMiddle() {
			return middle;
		}

		public void setMiddle(ChainMiddle middle) {
			this.middle = middle;
		}
	}

	public static class ChainMiddle {
		private String middleName;
		@InnerClass
		private ChainLeaf leaf;

		public ChainMiddle() {
		}

		public String getMiddleName() {
			return middleName;
		}

		public void setMiddleName(String middleName) {
			this.middleName = middleName;
		}

		public ChainLeaf getLeaf() {
			return leaf;
		}

		public void setLeaf(ChainLeaf leaf) {
			this.leaf = leaf;
		}
	}

	public static class ChainLeaf {
		private String leafValue;

		public ChainLeaf() {
		}

		public String getLeafValue() {
			return leafValue;
		}

		public void setLeafValue(String leafValue) {
			this.leafValue = leafValue;
		}
	}

	public static class OrderPart {
		private String orderId;
		private String orderStatus;

		public OrderPart() {
		}

		public String getOrderId() {
			return orderId;
		}

		public void setOrderId(String orderId) {
			this.orderId = orderId;
		}

		public String getOrderStatus() {
			return orderStatus;
		}

		public void setOrderStatus(String orderStatus) {
			this.orderStatus = orderStatus;
		}
	}

	public static class OrderLinePart {
		private String orderId;
		private String sku;

		public OrderLinePart() {
		}

		public String getOrderId() {
			return orderId;
		}

		public void setOrderId(String orderId) {
			this.orderId = orderId;
		}

		public String getSku() {
			return sku;
		}

		public void setSku(String sku) {
			this.sku = sku;
		}
	}

	public static class LineItemPart {
		private String orderId;
		private String sku;
		private String serial;

		public LineItemPart() {
		}

		public String getOrderId() {
			return orderId;
		}

		public void setOrderId(String orderId) {
			this.orderId = orderId;
		}

		public String getSku() {
			return sku;
		}

		public void setSku(String sku) {
			this.sku = sku;
		}

		public String getSerial() {
			return serial;
		}

		public void setSerial(String serial) {
			this.serial = serial;
		}
	}
}
