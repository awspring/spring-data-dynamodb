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
import io.awspring.cloud.dynamodb.core.mapping.InnerClass;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class MappingDynamoDbConverterTest {

	private DynamoDbMappingContext mappingContext;
	private MappingDynamoDbConverter mappingDynamoDbConverter;

	@BeforeEach
	void setUp() {
		this.mappingContext = new DynamoDbMappingContext();
		this.mappingDynamoDbConverter = new MappingDynamoDbConverter(mappingContext);
		this.mappingDynamoDbConverter.afterPropertiesSet();
	}

	@Test
	void insertTestClass() {
		LocalDate testDate = LocalDate.now();
		PlayerCardEntity testClassToBeInserted = new PlayerCardEntity("testID", testDate,
				Arrays.asList("test1", "test2"), Collections.singletonList("099"));
		Map<String, AttributeValue> mapToBeChecked = new HashMap<>();
		mappingDynamoDbConverter.write(testClassToBeInserted, mapToBeChecked);
		Assertions.assertEquals(mapToBeChecked.get("id").s(), "testID");
		Assertions.assertEquals(mapToBeChecked.get("registeredOn").s(), testDate.toString());
		Assertions.assertEquals(mapToBeChecked.get("tags").l().size(), 2);
	}

	@Test
	void chainedInnerClassesFlattenAllLevelsAndReconstruct() {
		ChainLeaf leaf = new ChainLeaf();
		leaf.setLeafValue("deep");
		ChainMiddle middle = new ChainMiddle();
		middle.setMiddleName("mid");
		middle.setLeaf(leaf);
		ChainOuter outer = new ChainOuter();
		outer.setId("o1");
		outer.setMiddle(middle);

		Map<String, AttributeValue> item = new HashMap<>();
		mappingDynamoDbConverter.write(outer, item);

		Assertions.assertEquals("o1", item.get("id").s());
		Assertions.assertEquals("mid", item.get("middleName").s());
		Assertions.assertEquals("deep", item.get("leafValue").s());
		Assertions.assertFalse(item.containsKey("middle"));
		Assertions.assertFalse(item.containsKey("leaf"));

		ChainOuter read = mappingDynamoDbConverter.read(ChainOuter.class, item);
		Assertions.assertNotNull(read.getMiddle());
		Assertions.assertEquals("mid", read.getMiddle().getMiddleName());
		Assertions.assertNotNull(read.getMiddle().getLeaf());
		Assertions.assertEquals("deep", read.getMiddle().getLeaf().getLeafValue());
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

}
