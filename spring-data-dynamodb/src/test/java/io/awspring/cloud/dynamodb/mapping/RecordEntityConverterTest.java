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

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class RecordEntityConverterTest {

	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		this.converter = new MappingDynamoDbConverter(mappingContext);
		this.converter.afterPropertiesSet();
	}

	@Table(tableName = "recordEntities")
	record RecordEntity(@PartitionKey String id, @SortKey String sortKey, int quantity, String note) {
	}

	@Test
	void recordEntityRoundTripsThroughWriteAndRead() {
		RecordEntity original = new RecordEntity("match-1", "item-1", 3, "gift-wrapped");

		Map<String, AttributeValue> item = new HashMap<>();
		this.converter.write(original, item);

		RecordEntity read = this.converter.read(RecordEntity.class, item);

		Assertions.assertNotNull(read);
		Assertions.assertEquals(original, read);
		Assertions.assertEquals("match-1", read.id());
		Assertions.assertEquals("item-1", read.sortKey());
		Assertions.assertEquals(3, read.quantity());
		Assertions.assertEquals("gift-wrapped", read.note());
	}

	@Test
	void recordEntityWithNullOptionalComponentRoundTrips() {
		RecordEntity original = new RecordEntity("match-2", "item-2", 0, null);

		Map<String, AttributeValue> item = new HashMap<>();
		this.converter.write(original, item);

		RecordEntity read = this.converter.read(RecordEntity.class, item);

		Assertions.assertNotNull(read);
		Assertions.assertEquals(original, read);
		Assertions.assertNull(read.note());
	}
}
