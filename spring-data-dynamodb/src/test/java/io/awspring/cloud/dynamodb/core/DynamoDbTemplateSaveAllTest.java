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
package io.awspring.cloud.dynamodb.core;

import static org.junit.jupiter.api.Assertions.*;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

public class DynamoDbTemplateSaveAllTest {

	@Table(tableName = "test_table")
	static class TestEntity {
		@PartitionKey
		private String id;
		private String data;

		public TestEntity() {
		}

		public TestEntity(String id, String data) {
			this.id = id;
			this.data = data;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getData() {
			return data;
		}

		public void setData(String data) {
			this.data = data;
		}
	}

	private DynamoDbTemplate template;
	private MockDynamoDbClient mockClient;
	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		mockClient = new MockDynamoDbClient();
		template = new DynamoDbTemplate(mockClient, converter);
	}

	@Test
	void saveAllWithItemsThatSucceedOnFirstCallWorksAsExpected() {
		List<TestEntity> entities = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			entities.add(new TestEntity("id-" + i, "data-" + i));
		}

		mockClient.setUnprocessedItemsSequence(Collections.emptyList());

		Iterable<TestEntity> result = template.saveAll(entities);

		assertIterableEquals(entities, result);
		assertEquals(1, mockClient.getCallCount(), "Should call batchWriteItem exactly once");
		assertEquals(5, mockClient.getLastRequestItemCount(), "Should write 5 items in one call");
	}

	@Test
	void saveAllRetriesWhenUnprocessedItemsReturnedAndSucceedsOnSecondAttempt() {
		List<TestEntity> entities = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			entities.add(new TestEntity("id-" + i, "data-" + i));
		}

		mockClient.setUnprocessedItemsSequence(Arrays.asList(2, 0));

		Iterable<TestEntity> result = template.saveAll(entities);

		assertIterableEquals(entities, result);
		assertEquals(2, mockClient.getCallCount(), "Should retry once after initial failure");
		assertEquals(2, mockClient.getLastRequestItemCount(), "Second call should retry 2 unprocessed items");
	}

	@Test
	void saveAllRetriesMultipleTimesWithExponentialBackoff() {
		List<TestEntity> entities = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			entities.add(new TestEntity("id-" + i, "data-" + i));
		}

		mockClient.setUnprocessedItemsSequence(Arrays.asList(3, 2, 1, 0));

		long startTime = System.currentTimeMillis();
		Iterable<TestEntity> result = template.saveAll(entities);
		long duration = System.currentTimeMillis() - startTime;

		assertIterableEquals(entities, result);
		assertEquals(4, mockClient.getCallCount(), "Should make 4 total calls");
		assertTrue(duration >= 300,
				"Should have applied exponential backoff delays (expected >= 300ms, got " + duration + "ms)");
	}

	@Test
	void saveAllThrowsAfterExhaustingRetriesWhenUnprocessedItemsNeverClear() {
		List<TestEntity> entities = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			entities.add(new TestEntity("id-" + i, "data-" + i));
		}

		List<Integer> neverSucceeds = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			neverSucceeds.add(1);
		}
		mockClient.setUnprocessedItemsSequence(neverSucceeds);

		DataAccessResourceFailureException exception = assertThrows(DataAccessResourceFailureException.class,
				() -> template.saveAll(entities));

		assertTrue(exception.getMessage().contains("Failed to write 1 item(s)"),
				"Exception message should indicate the number of unprocessed items");
		assertTrue(exception.getMessage().contains("after 8 attempts"),
				"Exception message should indicate the number of attempts");
		assertEquals(8, mockClient.getCallCount(), "Should exhaust all 8 retry attempts");
	}

	@Test
	void saveAllChunksMoreThan25EntitiesIntoMultipleBatchWriteItemRequests() {
		List<TestEntity> entities = new ArrayList<>();
		for (int i = 0; i < 60; i++) {
			entities.add(new TestEntity("id-" + i, "data-" + i));
		}

		mockClient.setUnprocessedItemsSequence(Collections.emptyList());

		Iterable<TestEntity> result = template.saveAll(entities);

		assertIterableEquals(entities, result);
		assertEquals(3, mockClient.getCallCount(), "Should split 60 items into 3 batch calls");

		List<Integer> requestSizes = mockClient.getAllRequestItemCounts();
		assertEquals(Arrays.asList(25, 25, 10), requestSizes, "Should send 25 + 25 + 10 items across 3 requests");
	}

	@Test
	void saveAllHandlesExactly25ItemsInSingleBatch() {
		List<TestEntity> entities = new ArrayList<>();
		for (int i = 0; i < 25; i++) {
			entities.add(new TestEntity("id-" + i, "data-" + i));
		}

		mockClient.setUnprocessedItemsSequence(Collections.emptyList());

		Iterable<TestEntity> result = template.saveAll(entities);

		assertIterableEquals(entities, result);
		assertEquals(1, mockClient.getCallCount(), "Should send exactly 25 items in one batch");
		assertEquals(25, mockClient.getLastRequestItemCount());
	}

	@Test
	void saveAllHandles26ItemsInTwoBatches() {
		List<TestEntity> entities = new ArrayList<>();
		for (int i = 0; i < 26; i++) {
			entities.add(new TestEntity("id-" + i, "data-" + i));
		}

		mockClient.setUnprocessedItemsSequence(Collections.emptyList());

		Iterable<TestEntity> result = template.saveAll(entities);

		assertIterableEquals(entities, result);
		assertEquals(2, mockClient.getCallCount(), "Should split 26 items into 2 batch calls");

		List<Integer> requestSizes = mockClient.getAllRequestItemCounts();
		assertEquals(Arrays.asList(25, 1), requestSizes, "Should send 25 + 1 items across 2 requests");
	}

	@Test
	void saveAllWithEmptyListDoesNotCallDynamoDb() {
		List<TestEntity> entities = Collections.emptyList();

		Iterable<TestEntity> result = template.saveAll(entities);

		assertIterableEquals(entities, result);
		assertEquals(0, mockClient.getCallCount(), "Should not call batchWriteItem for empty list");
	}

	@Test
	void saveAllRetriesOnlyUnprocessedItemsNotEntireBatch() {
		List<TestEntity> entities = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			entities.add(new TestEntity("id-" + i, "data-" + i));
		}

		mockClient.setUnprocessedItemsSequence(Arrays.asList(2, 0));

		template.saveAll(entities);

		assertEquals(2, mockClient.getCallCount());
		assertEquals(5, mockClient.getAllRequestItemCounts().get(0), "First call should have 5 items");
		assertEquals(2, mockClient.getAllRequestItemCounts().get(1),
				"Second call should only retry 2 unprocessed items");
	}

	static class MockDynamoDbClient implements DynamoDbClient {
		private int callCount = 0;
		private List<Integer> unprocessedItemsSequence = new ArrayList<>();
		private List<Integer> requestItemCounts = new ArrayList<>();

		public void setUnprocessedItemsSequence(List<Integer> sequence) {
			this.unprocessedItemsSequence = new ArrayList<>(sequence);
		}

		public int getCallCount() {
			return callCount;
		}

		public int getLastRequestItemCount() {
			return requestItemCounts.isEmpty() ? 0 : requestItemCounts.get(requestItemCounts.size() - 1);
		}

		public List<Integer> getAllRequestItemCounts() {
			return new ArrayList<>(requestItemCounts);
		}

		@Override
		public BatchWriteItemResponse batchWriteItem(BatchWriteItemRequest request) {
			callCount++;
			int itemCount = 0;
			for (List<WriteRequest> requests : request.requestItems().values()) {
				itemCount += requests.size();
			}
			requestItemCounts.add(itemCount);

			int unprocessedCount = 0;
			if (callCount - 1 < unprocessedItemsSequence.size()) {
				unprocessedCount = unprocessedItemsSequence.get(callCount - 1);
			}

			if (unprocessedCount == 0) {
				return BatchWriteItemResponse.builder().unprocessedItems(Collections.emptyMap()).build();
			}
			else {
				Map<String, List<WriteRequest>> unprocessedItems = new HashMap<>();
				for (Map.Entry<String, List<WriteRequest>> entry : request.requestItems().entrySet()) {
					List<WriteRequest> requestList = entry.getValue();
					int startIndex = Math.max(0, requestList.size() - unprocessedCount);
					List<WriteRequest> unprocessed = new ArrayList<>(
							requestList.subList(startIndex, requestList.size()));
					unprocessedItems.put(entry.getKey(), unprocessed);
				}
				return BatchWriteItemResponse.builder().unprocessedItems(unprocessedItems).build();
			}
		}

		@Override
		public String serviceName() {
			return "dynamodb";
		}

		@Override
		public void close() {
		}
	}
}
