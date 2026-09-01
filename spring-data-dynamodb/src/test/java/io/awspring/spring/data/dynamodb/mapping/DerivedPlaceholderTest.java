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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.Derived;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@DisplayName("@Derived -- placeholder decomposition and bootstrap validation")
class DerivedPlaceholderTest {

	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		this.converter = new MappingDynamoDbConverter(new DynamoDbMappingContext());
		this.converter.afterPropertiesSet();
	}

	@Test
	void derivedPlaceholdersAreNotWrittenButStillRoundTrip() {
		DerivedLineRow row = new DerivedLineRow();
		row.pk = "ORDER#9876";
		row.orderId = "9876";
		row.lineId = "abc";
		row.sku = "WIDGET-1";

		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(row, item);

		assertEquals("ORDER#9876#LINE#abc", item.get("sk").s(), "the templated column is still composed");
		assertFalse(item.containsKey("orderId"), "@Derived placeholder must not be written");
		assertFalse(item.containsKey("lineId"), "@Derived placeholder must not be written");
		assertEquals("WIDGET-1", item.get("sku").s(), "ordinary properties are unaffected");

		DerivedLineRow restored = converter.read(DerivedLineRow.class, item);
		assertNotNull(restored);
		assertEquals("9876", restored.orderId, "decomposed back out of the composed sort key");
		assertEquals("abc", restored.lineId, "decomposed back out of the composed sort key");
		assertEquals("WIDGET-1", restored.sku);
	}

	@Test
	void placeholdersAreStillPersistedWithoutTheAnnotation() {
		PlainLineRow row = new PlainLineRow();
		row.pk = "ORDER#9876";
		row.orderId = "9876";
		row.lineId = "abc";

		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(row, item);

		assertEquals("ORDER#9876#LINE#abc", item.get("sk").s());
		assertTrue(item.containsKey("orderId"), "default behaviour is unchanged");
		assertTrue(item.containsKey("lineId"), "default behaviour is unchanged");
	}

	@Test
	void derivedOnANonPlaceholderIsRejectedAtBootstrap() {
		assertBootstrapRejection(DerivedNonPlaceholder.class, "not a @SortKeyTemplate placeholder");
	}

	@Test
	void derivedOnAKeyPropertyIsRejectedAtBootstrap() {
		assertBootstrapRejection(DerivedKey.class, "is a key property");
	}

	@Test
	void derivedOnAPrimitiveIsRejectedAtBootstrap() {
		assertBootstrapRejection(DerivedPrimitive.class, "primitive type");
	}

	private static void assertBootstrapRejection(Class<?> entityType, String expectedFragment) {
		MappingException exception = assertThrows(MappingException.class,
				() -> new DynamoDbMappingContext().getRequiredPersistentEntity(entityType));

		StringBuilder messages = new StringBuilder();
		for (Throwable current = exception; current != null; current = current.getCause()) {
			messages.append(current.getMessage()).append('\n');
			for (Throwable suppressed : current.getSuppressed()) {
				messages.append(suppressed.getMessage()).append('\n');
			}
		}

		assertTrue(messages.toString().contains(expectedFragment),
				() -> "expected a rejection mentioning \"" + expectedFragment + "\" but got:\n" + messages);
	}

	@Table(tableName = "commerce")
	@SortKeyTemplate("ORDER#{orderId}#LINE#{lineId}")
	static class DerivedLineRow {
		@PartitionKey
		String pk;
		String sk;
		@Derived
		String orderId;
		@Derived
		String lineId;
		String sku;

		public DerivedLineRow() {
		}
	}

	@Table(tableName = "commerce_plain")
	@SortKeyTemplate("ORDER#{orderId}#LINE#{lineId}")
	static class PlainLineRow {
		@PartitionKey
		String pk;
		String sk;
		String orderId;
		String lineId;

		public PlainLineRow() {
		}
	}

	@Table(tableName = "derived_non_placeholder")
	@SortKeyTemplate("ORDER#{orderId}")
	static class DerivedNonPlaceholder {
		@PartitionKey
		String pk;
		String sk;
		String orderId;
		@Derived
		String status;

		public DerivedNonPlaceholder() {
		}
	}

	@Table(tableName = "derived_key")
	@SortKeyTemplate("ORDER#{orderId}")
	static class DerivedKey {
		@Derived
		@PartitionKey
		String pk;
		String sk;
		String orderId;

		public DerivedKey() {
		}
	}

	@Table(tableName = "derived_primitive")
	@SortKeyTemplate("LINE#{lineNo}")
	static class DerivedPrimitive {
		@PartitionKey
		String pk;
		String sk;
		@Derived
		int lineNo;

		public DerivedPrimitive() {
		}
	}
}
