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
package io.awspring.cloud.dynamodb.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.awspring.cloud.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.AggregateItem;
import io.awspring.cloud.dynamodb.core.mapping.AggregateTable;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.support.DynamoDbEntityInformation;
import io.awspring.cloud.dynamodb.request.DynamoDbPageRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbQueryRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("SimpleAggregateRepository")
class SimpleAggregateRepositoryTest {

	private static final String TABLE_NAME = "commerce";
	private static final String PARTITION_KEY_VALUE = "CUSTOMER#1";
	private static final String SORT_KEY_VALUE = "ORDER#9876";
	private static final String SORT_KEY_LOW = "ORDER#1";
	private static final String SORT_KEY_HIGH = "ORDER#9";
	private static final String SORT_KEY_PREFIX = "ORDER#";

	@Table(tableName = TABLE_NAME)
	static class OrderRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String status;
	}

	@AggregateTable(tableName = TABLE_NAME, partitionKey = "pk", sortKey = "sk")
	static class OrderAggregate {
		@AggregateItem(regex = "ORDER#[^#]+")
		OrderRow order;
	}

	private DynamoDbOperations operations;
	private SimpleAggregateRepository<OrderAggregate> repository;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();

		this.operations = mock(DynamoDbOperations.class);
		when(operations.getConverter()).thenReturn(converter);

		DynamoDbEntityInformation<OrderAggregate, ?> entityInformation = mock(DynamoDbEntityInformation.class);
		when(entityInformation.getJavaType()).thenReturn(OrderAggregate.class);

		this.repository = new SimpleAggregateRepository<>(entityInformation, operations);
	}

	private DynamoDbQueryRequest captureRequest(Runnable invocation) {
		ArgumentCaptor<DynamoDbQueryRequest> captor = ArgumentCaptor.forClass(DynamoDbQueryRequest.class);
		when(operations.queryAggregate(eq(OrderAggregate.class), captor.capture(), any(DynamoDbPageRequest.class)))
				.thenReturn(null);
		invocation.run();
		return captor.getValue();
	}

	@Nested
	@DisplayName("Partition key only queries")
	class PartitionKeyOnlyTests {

		@Test
		@DisplayName("findByPartitionKey derives an equality key condition")
		void partitionKeyOnlyDerivesEqualityCondition() {
			// Act
			DynamoDbQueryRequest request = captureRequest(() -> repository.findByPartitionKey(PARTITION_KEY_VALUE));

			// Assert
			assertAll(() -> assertEquals("#pk = :pk", request.getKeyConditionExpression()),
					() -> assertEquals(Map.of("#pk", "pk"), request.getExpressionAttributeNames()),
					() -> assertEquals(Map.of(":pk", PARTITION_KEY_VALUE), request.getExpressionAttributeValues()));
		}

		@Test
		@DisplayName("existsByPartitionKey derives an equality key condition")
		void existsByPartitionKeyDerivesEqualityCondition() {
			// Act
			DynamoDbQueryRequest request = captureRequest(() -> repository.existsByPartitionKey(PARTITION_KEY_VALUE));

			// Assert
			assertAll(() -> assertEquals("#pk = :pk", request.getKeyConditionExpression()),
					() -> assertEquals(Map.of("#pk", "pk"), request.getExpressionAttributeNames()),
					() -> assertEquals(Map.of(":pk", PARTITION_KEY_VALUE), request.getExpressionAttributeValues()));
		}
	}

	@Nested
	@DisplayName("Partition key + sort key queries")
	class PartitionAndSortKeyTests {

		@Test
		@DisplayName("findByPartitionKeyAndSortKey derives equality on both keys")
		void partitionKeyAndSortKeyDerivesEqualityCondition() {
			// Act
			DynamoDbQueryRequest request = captureRequest(
					() -> repository.findByPartitionKeyAndSortKey(PARTITION_KEY_VALUE, SORT_KEY_VALUE));

			// Assert
			assertAll(() -> assertEquals("#pk = :pk AND #sk = :sk", request.getKeyConditionExpression()),
					() -> assertEquals(Map.of("#pk", "pk", "#sk", "sk"), request.getExpressionAttributeNames()),
					() -> assertEquals(Map.of(":pk", PARTITION_KEY_VALUE, ":sk", SORT_KEY_VALUE),
							request.getExpressionAttributeValues()));
		}

		@Test
		@DisplayName("findByPartitionKeyAndSortKeyBetween derives a BETWEEN range condition")
		void partitionKeyAndSortKeyBetweenDerivesRangeCondition() {
			// Act
			DynamoDbQueryRequest request = captureRequest(() -> repository
					.findByPartitionKeyAndSortKeyBetween(PARTITION_KEY_VALUE, SORT_KEY_LOW, SORT_KEY_HIGH));

			// Assert
			assertAll(() -> assertEquals("#pk = :pk AND #sk BETWEEN :lo AND :hi", request.getKeyConditionExpression()),
					() -> assertEquals(Map.of("#pk", "pk", "#sk", "sk"), request.getExpressionAttributeNames()),
					() -> assertEquals(Map.of(":pk", PARTITION_KEY_VALUE, ":lo", SORT_KEY_LOW, ":hi", SORT_KEY_HIGH),
							request.getExpressionAttributeValues()));
		}

		@Test
		@DisplayName("findByPartitionKeyAndSortKeyStartingWith derives a begins_with condition")
		void partitionKeyAndSortKeyStartingWithDerivesBeginsWithCondition() {
			// Act
			DynamoDbQueryRequest request = captureRequest(
					() -> repository.findByPartitionKeyAndSortKeyStartingWith(PARTITION_KEY_VALUE, SORT_KEY_PREFIX));

			// Assert
			assertAll(() -> assertEquals("#pk = :pk AND begins_with(#sk, :p)", request.getKeyConditionExpression()),
					() -> assertEquals(Map.of("#pk", "pk", "#sk", "sk"), request.getExpressionAttributeNames()),
					() -> assertEquals(Map.of(":pk", PARTITION_KEY_VALUE, ":p", SORT_KEY_PREFIX),
							request.getExpressionAttributeValues()));
		}
	}
}
