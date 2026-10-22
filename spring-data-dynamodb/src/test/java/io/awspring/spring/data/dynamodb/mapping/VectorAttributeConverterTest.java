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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.Score;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.core.mapping.VectorIndex;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@DisplayName("Vector attribute mapping (float[]/double[] <-> L of N, virtual @Score)")
class VectorAttributeConverterTest {

	@Table(tableName = "products")
	static class Product {
		@PartitionKey
		String id;
		@VectorIndex(indexName = "DescriptionIndex")
		float[] embedding;
		double[] precise;
		@Score
		Double score;
	}

	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		this.converter = new MappingDynamoDbConverter(new DynamoDbMappingContext());
		this.converter.afterPropertiesSet();
	}

	@Test
	@DisplayName("float[] and double[] are written as a List of Numbers and @Score is never written")
	void write_vectorsAsListOfNumbers_scoreOmitted() {
		Product product = new Product();
		product.id = "p1";
		product.embedding = new float[] { 0.25f, -1.5f };
		product.precise = new double[] { 0.125d };
		product.score = 0.9d;

		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(product, item);

		assertAll(
				() -> assertEquals(List.of(AttributeValue.fromN("0.25"), AttributeValue.fromN("-1.5")),
						item.get("embedding").l()),
				() -> assertEquals(List.of(AttributeValue.fromN("0.125")), item.get("precise").l()),
				() -> assertFalse(item.containsKey("score")));
	}

	@Test
	@DisplayName("A List of Numbers round-trips into float[] and double[]; @Score is not read from the item")
	void read_listOfNumbers_roundTrips() {
		Map<String, AttributeValue> item = Map.of("id", AttributeValue.fromS("p1"), "embedding",
				AttributeValue.fromL(List.of(AttributeValue.fromN("0.25"), AttributeValue.fromN("-1.5"))), "precise",
				AttributeValue.fromL(List.of(AttributeValue.fromN("0.125"))), "score", AttributeValue.fromN("0.4"));

		Product read = converter.read(Product.class, item);

		assertAll(() -> assertArrayEquals(new float[] { 0.25f, -1.5f }, read.embedding),
				() -> assertArrayEquals(new double[] { 0.125d }, read.precise), () -> assertNull(read.score));
	}

	@Test
	@DisplayName("An absent vector attribute (not projected by SearchVectors) reads as null")
	void read_absentVector_isNull() {
		Product read = converter.read(Product.class, Map.of("id", AttributeValue.fromS("p1")));

		assertNull(read.embedding);
	}

	@Test
	@DisplayName("A List element that is not a Number fails with a MappingException naming the element")
	void read_nonNumericElement_failsWithMappingException() {
		Map<String, AttributeValue> item = Map.of("id", AttributeValue.fromS("p1"), "embedding",
				AttributeValue.fromL(List.of(AttributeValue.fromN("0.25"), AttributeValue.fromS("oops"))));

		MappingException exception = assertThrows(MappingException.class, () -> converter.read(Product.class, item));

		assertEquals("Cannot read float[] from a List (L) whose element 1 does not carry a Number (N) value",
				exception.getMessage());
	}
}
