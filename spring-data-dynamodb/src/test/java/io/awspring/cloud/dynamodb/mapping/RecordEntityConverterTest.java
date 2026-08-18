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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class RecordEntityConverterTest {

	private static final String TABLE_NAME = "recordEntities";
	private static final String ID_1 = "match-1";
	private static final String SORT_KEY_1 = "item-1";
	private static final int QUANTITY_1 = 3;
	private static final String NOTE_1 = "gift-wrapped";
	private static final String ID_2 = "match-2";
	private static final String SORT_KEY_2 = "item-2";

	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		this.converter = new MappingDynamoDbConverter(mappingContext);
		this.converter.afterPropertiesSet();
	}

	@Nested
	@DisplayName("RoundTrip")
	class RoundTrip {

		@Test
		@DisplayName("record entity round-trips through write and read")
		void roundTrip_allFieldsPopulated_reconstructsIdenticalRecord() {
			RecordEntity original = new RecordEntity(ID_1, SORT_KEY_1, QUANTITY_1, NOTE_1);

			Map<String, AttributeValue> item = new HashMap<>();
			converter.write(original, item);
			RecordEntity read = converter.read(RecordEntity.class, item);

			assertAll(() -> assertNotNull(read), () -> assertEquals(original, read),
					() -> assertEquals(ID_1, read.id()), () -> assertEquals(SORT_KEY_1, read.sortKey()),
					() -> assertEquals(QUANTITY_1, read.quantity()), () -> assertEquals(NOTE_1, read.note()));
		}

		@Test
		@DisplayName("record with null optional component round-trips correctly")
		void roundTrip_nullOptionalComponent_preservesNull() {
			RecordEntity original = new RecordEntity(ID_2, SORT_KEY_2, 0, null);

			Map<String, AttributeValue> item = new HashMap<>();
			converter.write(original, item);
			RecordEntity read = converter.read(RecordEntity.class, item);

			assertAll(() -> assertNotNull(read), () -> assertEquals(original, read), () -> assertNull(read.note()));
		}
	}

	// --- Test fixtures ---

	@Table(tableName = TABLE_NAME)
	record RecordEntity(@PartitionKey String id, @SortKey String sortKey, int quantity, String note) {
	}
}
