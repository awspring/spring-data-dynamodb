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
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class CollectionRoundTripTest {

	@Table(tableName = "test_collections")
	static class EntityWithCollections {
		@PartitionKey
		String id;
		Set<Integer> integerSet;
		List<Integer> integerList;

		public EntityWithCollections() {
		}

		public EntityWithCollections(String id, Set<Integer> integerSet, List<Integer> integerList) {
			this.id = id;
			this.integerSet = integerSet;
			this.integerList = integerList;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			EntityWithCollections that = (EntityWithCollections) o;
			return Objects.equals(id, that.id) && Objects.equals(integerSet, that.integerSet)
					&& Objects.equals(integerList, that.integerList);
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, integerSet, integerList);
		}
	}

	private DynamoDbMappingContext mappingContext;
	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		this.mappingContext = new DynamoDbMappingContext();
		this.converter = new MappingDynamoDbConverter(mappingContext);
		this.converter.afterPropertiesSet();
	}

	@Test
	void setOfIntegersRoundTripsCorrectly() {
		EntityWithCollections original = new EntityWithCollections("test-1", Set.of(1, 2, 3), null);

		Map<String, AttributeValue> attributeMap = new HashMap<>();
		converter.write(original, attributeMap);

		EntityWithCollections restored = converter.read(EntityWithCollections.class, attributeMap);

		assertNotNull(restored);
		assertEquals(original.id, restored.id);
		assertNotNull(restored.integerSet);
		assertEquals(3, restored.integerSet.size());
		assertTrue(restored.integerSet.contains(1));
		assertTrue(restored.integerSet.contains(2));
		assertTrue(restored.integerSet.contains(3));
		restored.integerSet.forEach(val -> assertInstanceOf(Integer.class, val));
	}

	@Test
	void listOfIntegersRoundTripsCorrectly() {
		EntityWithCollections original = new EntityWithCollections("test-2", null, List.of(4, 5, 6));

		Map<String, AttributeValue> attributeMap = new HashMap<>();
		converter.write(original, attributeMap);

		EntityWithCollections restored = converter.read(EntityWithCollections.class, attributeMap);

		assertNotNull(restored);
		assertEquals(original.id, restored.id);
		assertNotNull(restored.integerList);
		assertEquals(List.of(4, 5, 6), restored.integerList);
		restored.integerList.forEach(val -> assertInstanceOf(Integer.class, val));
	}

	@Test
	void bothCollectionsRoundTripCorrectly() {
		EntityWithCollections original = new EntityWithCollections("test-3",
				new LinkedHashSet<>(Arrays.asList(10, 20, 30)), new ArrayList<>(Arrays.asList(100, 200, 300)));

		Map<String, AttributeValue> attributeMap = new HashMap<>();
		converter.write(original, attributeMap);

		EntityWithCollections restored = converter.read(EntityWithCollections.class, attributeMap);

		assertNotNull(restored);
		assertEquals(original.id, restored.id);
		assertEquals(original.integerSet, restored.integerSet);
		assertEquals(original.integerList, restored.integerList);
		restored.integerSet.forEach(val -> assertInstanceOf(Integer.class, val));
		restored.integerList.forEach(val -> assertInstanceOf(Integer.class, val));
	}

	@Test
	void emptyCollectionsRoundTripCorrectly() {
		EntityWithCollections original = new EntityWithCollections("test-4", new HashSet<>(), new ArrayList<>());

		Map<String, AttributeValue> attributeMap = new HashMap<>();
		converter.write(original, attributeMap);

		EntityWithCollections restored = converter.read(EntityWithCollections.class, attributeMap);

		assertNotNull(restored);
		assertEquals(original.id, restored.id);
		assertNotNull(restored.integerSet);
		assertNotNull(restored.integerList);
		assertTrue(restored.integerSet.isEmpty());
		assertTrue(restored.integerList.isEmpty());
	}
}
