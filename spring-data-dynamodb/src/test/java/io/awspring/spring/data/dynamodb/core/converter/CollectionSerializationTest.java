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
package io.awspring.spring.data.dynamodb.core.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@DisplayName("Collection serialization -- List/Set/Map round-trips")
class CollectionSerializationTest {

	private DynamoDbMappingContext mappingContext;
	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		this.mappingContext = new DynamoDbMappingContext();
		this.converter = new MappingDynamoDbConverter(mappingContext);
		this.converter.afterPropertiesSet();
	}

	@Test
	void setOfStringsWritesAsStringSet() {
		Set<String> input = new LinkedHashSet<>(Arrays.asList("a", "b", "c"));
		AttributeValue av = converter.toAttributeValue(input, false);
		assertTrue(av.hasSs());
		assertEquals(3, av.ss().size());
	}

	@Test
	void setOfIntegersWritesAsNumberSet() {
		Set<Integer> input = new LinkedHashSet<>(Arrays.asList(1, 2, 3));
		AttributeValue av = converter.toAttributeValue(input, false);
		assertTrue(av.hasNs());
		assertFalse(av.hasL());
		assertEquals(3, av.ns().size());
	}

	@Test
	void setOfLongsWritesAsNumberSet() {
		Set<Long> input = new LinkedHashSet<>(Arrays.asList(100L, 200L, 300L));
		AttributeValue av = converter.toAttributeValue(input, false);
		assertTrue(av.hasNs());
	}

	@Test
	void setOfDoublesWritesAsNumberSet() {
		Set<Double> input = new LinkedHashSet<>(Arrays.asList(1.5, 2.5, 3.5));
		AttributeValue av = converter.toAttributeValue(input, false);
		assertTrue(av.hasNs());
	}

	@Test
	void setOfByteArraysWritesAsBinarySet() {
		Set<byte[]> input = new LinkedHashSet<>();
		input.add(new byte[] { 1, 2, 3 });
		input.add(new byte[] { 4, 5, 6 });

		AttributeValue av = converter.toAttributeValue(input, false);
		assertTrue(av.hasBs());
		assertEquals(2, av.bs().size());
	}

	@Test
	void mixedTypeSetFallsBackToList() {
		Set<Object> input = new LinkedHashSet<>();
		input.add("string");
		input.add(42);

		AttributeValue av = converter.toAttributeValue(input, false);
		assertTrue(av.hasL());
		assertFalse(av.hasSs());
		assertFalse(av.hasNs());
	}

	@Test
	void emptySetWritesAsEmptyList() {
		Set<String> empty = new HashSet<>();
		AttributeValue av = converter.toAttributeValue(empty, false);
		assertFalse(av.hasSs());
		assertTrue(av.hasL());
		assertEquals(0, av.l().size());
	}

	@Test
	void emptySetRoundTripsToEmptySetOnRead() {
		EntityWithIntegerSet original = new EntityWithIntegerSet();
		original.id = "e1";
		original.integerSet = new HashSet<>();

		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(original, item);

		EntityWithIntegerSet restored = converter.read(EntityWithIntegerSet.class, item);
		assertNotNull(restored);
		assertNotNull(restored.integerSet);
		assertTrue(restored.integerSet.isEmpty());
	}

	@Test
	void binarySetRoundTrips() {
		EntityWithBinarySet source = new EntityWithBinarySet();
		source.id = "e1";
		source.binarySet = new LinkedHashSet<>();
		source.binarySet.add(new byte[] { 1, 2 });
		source.binarySet.add(new byte[] { 3, 4 });

		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(source, item);

		assertTrue(item.get("binarySet").hasBs());

		EntityWithBinarySet restored = converter.read(EntityWithBinarySet.class, item);
		assertNotNull(restored);
		assertEquals(2, restored.binarySet.size());
	}

	@Test
	void setFromListPreservesLinkedHashSetOrdering() {
		Map<String, AttributeValue> item = new HashMap<>();
		item.put("id", AttributeValue.builder().s("e1").build());
		item.put("integerSet", AttributeValue.builder().l(AttributeValue.builder().n("3").build(),
				AttributeValue.builder().n("1").build(), AttributeValue.builder().n("2").build()).build());

		EntityWithIntegerSet restored = converter.read(EntityWithIntegerSet.class, item);
		assertNotNull(restored);
		assertInstanceOf(LinkedHashSet.class, restored.integerSet);
	}

	@Test
	void mapInterfaceIsDeserializedAsLinkedHashMap() {
		EntityWithMap entity = new EntityWithMap();
		entity.id = "e1";
		entity.mapField = new LinkedHashMap<>();
		entity.mapField.put("k1", "v1");
		entity.mapField.put("k2", "v2");

		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(entity, item);

		EntityWithMap restored = converter.read(EntityWithMap.class, item);
		assertNotNull(restored);
		assertEquals(2, restored.mapField.size());
		assertEquals("v1", restored.mapField.get("k1"));
	}

	@Test
	void concreteTreeMapTypeIsInstantiated() {
		EntityWithTreeMap entity = new EntityWithTreeMap();
		entity.id = "e1";
		entity.treeMapField = new TreeMap<>();
		entity.treeMapField.put("k1", "v1");

		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(entity, item);

		EntityWithTreeMap restored = converter.read(EntityWithTreeMap.class, item);
		assertNotNull(restored);
		assertInstanceOf(TreeMap.class, restored.treeMapField);
	}

	@Test
	void mapWithNestedListValueIsPreserved() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("nested", Arrays.asList("a", "b"));

		AttributeValue av = converter.toAttributeValue(map, false);
		assertTrue(av.hasM());
		assertTrue(av.m().get("nested").hasL());
	}

	@Test
	void mapWithNestedMapValueIsPreserved() {
		Map<String, Object> outer = new LinkedHashMap<>();
		Map<String, Object> inner = new LinkedHashMap<>();
		inner.put("innerKey", "innerValue");
		outer.put("outerKey", inner);

		AttributeValue av = converter.toAttributeValue(outer, false);
		assertTrue(av.hasM());
		assertTrue(av.m().get("outerKey").hasM());
	}

	@Test
	void uninstantiableConcreteMapThrowsMappingException() {
		AttributeValue m = AttributeValue.builder().m(Map.of("k", AttributeValue.builder().s("v").build())).build();

		Map<String, AttributeValue> item = new HashMap<>();
		item.put("id", AttributeValue.builder().s("e1").build());
		item.put("mapField", m);

		assertThrows(MappingException.class, () -> converter.read(EntityWithBadMap.class, item));
	}

	@Table(tableName = "sets_test")
	public static class EntityWithIntegerSet {
		@PartitionKey
		public String id;
		public Set<Integer> integerSet;

		public EntityWithIntegerSet() {
		}
	}

	@Table(tableName = "binary_set_test")
	public static class EntityWithBinarySet {
		@PartitionKey
		public String id;
		public Set<byte[]> binarySet;

		public EntityWithBinarySet() {
		}
	}

	@Table(tableName = "map_test")
	public static class EntityWithMap {
		@PartitionKey
		public String id;
		public Map<String, String> mapField;

		public EntityWithMap() {
		}
	}

	@Table(tableName = "tree_map_test")
	public static class EntityWithTreeMap {
		@PartitionKey
		public String id;
		public TreeMap<String, String> treeMapField;

		public EntityWithTreeMap() {
		}
	}

	@Table(tableName = "bad_map_test")
	public static class EntityWithBadMap {
		@PartitionKey
		public String id;
		public MapWithNoNoArgConstructor mapField;

		public EntityWithBadMap() {
		}
	}

	public static class MapWithNoNoArgConstructor extends HashMap<String, String> {
		private static final long serialVersionUID = 1L;

		public MapWithNoNoArgConstructor(String required) {
			super();
		}
	}
}
