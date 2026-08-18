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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.annotation.Version;
import org.springframework.util.backoff.ExponentialBackOff;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@DisplayName("DynamoDbTemplate.saveAll -- batch-write semantics")
class DynamoDbTemplateSaveAllTest {

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

	/** A second item kind living in the very same physical table -- the single-table-design case. */
	@Table(tableName = "test_table")
	static class SiblingEntity {
		@PartitionKey
		private String id;
		private String label;

		public SiblingEntity() {
		}

		public SiblingEntity(String id, String label) {
			this.id = id;
			this.label = label;
		}

		public String getId() {
			return id;
		}

		public String getLabel() {
			return label;
		}
	}

	@Table(tableName = "other_table")
	static class OtherTableEntity {
		@PartitionKey
		private String id;

		public OtherTableEntity() {
		}

		public OtherTableEntity(String id) {
			this.id = id;
		}

		public String getId() {
			return id;
		}
	}

	@Table(tableName = "test_table")
	static class VersionedEntity {
		@PartitionKey
		private String id;
		@Version
		private Long version;

		public VersionedEntity() {
		}

		public VersionedEntity(String id) {
			this.id = id;
		}

		public String getId() {
			return id;
		}

		public Long getVersion() {
			return version;
		}

		public void setVersion(Long version) {
			this.version = version;
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

		// Fast, deterministic pacing for tests: zero delay, up to 8 retries (9 total calls).
		ExponentialBackOff fast = new ExponentialBackOff(0L, 1.0);
		fast.setMaxInterval(0L);
		fast.setJitter(0L);
		fast.setMaxAttempts(8);
		template.setBatchWriteBackOff(fast);
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

		// Real exponential delays (50, 100, 200), jitter disabled for a deterministic lower bound.
		ExponentialBackOff realBackOff = new ExponentialBackOff(50L, 2.0);
		realBackOff.setMaxInterval(5000L);
		realBackOff.setJitter(0L);
		realBackOff.setMaxAttempts(8);
		template.setBatchWriteBackOff(realBackOff);

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
		assertTrue(exception.getMessage().contains("after 9 attempt(s)"),
				"Exception message should indicate the actual number of attempts (1 original + 8 retries)");
		assertEquals(9, mockClient.getCallCount(), "Should make 1 original call plus 8 retries");
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

	@Test
	void saveAllBatchesMixedItemKindsSharingOneTableIntoASingleRequest() {
		List<Object> entities = List.of(new TestEntity("id-0", "data-0"), new SiblingEntity("id-1", "label-1"),
				new TestEntity("id-2", "data-2"));

		mockClient.setUnprocessedItemsSequence(Collections.emptyList());

		Iterable<Object> result = template.saveAll(entities);

		assertIterableEquals(entities, result);
		assertEquals(1, mockClient.getCallCount(), "Mixed item kinds on one table belong in one BatchWriteItem");
		assertEquals(3, mockClient.getLastRequestItemCount());
		assertEquals(List.of(List.of("test_table")), mockClient.getAllRequestTableNames());
	}

	@Test
	void saveAllGroupsEntitiesByTableIntoOneRequestPerTable() {
		List<Object> entities = List.of(new TestEntity("id-0", "data-0"), new OtherTableEntity("id-1"),
				new SiblingEntity("id-2", "label-2"));

		mockClient.setUnprocessedItemsSequence(Collections.emptyList());

		template.saveAll(entities);

		assertEquals(2, mockClient.getCallCount(), "Each table gets its own BatchWriteItem request");
		assertEquals(List.of(List.of("test_table"), List.of("other_table")), mockClient.getAllRequestTableNames(),
				"Groups are batched in first-seen order");
		assertEquals(Arrays.asList(2, 1), mockClient.getAllRequestItemCounts());
	}

	@Test
	void saveAllRejectsAVersionedEntityAnywhereInTheBatchWithoutWritingAnything() {
		List<Object> entities = List.of(new TestEntity("id-0", "data-0"), new VersionedEntity("id-1"));

		InvalidDataAccessApiUsageException exception = assertThrows(InvalidDataAccessApiUsageException.class,
				() -> template.saveAll(entities));

		assertTrue(exception.getMessage().contains(VersionedEntity.class.getName()), exception.getMessage());
		assertEquals(0, mockClient.getCallCount(), "Must fail before issuing any write");
	}

	static class MockDynamoDbClient implements DynamoDbClient {
		private int callCount = 0;
		private List<Integer> unprocessedItemsSequence = new ArrayList<>();
		private List<Integer> requestItemCounts = new ArrayList<>();
		private List<List<String>> requestTableNames = new ArrayList<>();

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

		public List<List<String>> getAllRequestTableNames() {
			return new ArrayList<>(requestTableNames);
		}

		@Override
		public BatchWriteItemResponse batchWriteItem(BatchWriteItemRequest request) {
			callCount++;
			int itemCount = 0;
			for (List<WriteRequest> requests : request.requestItems().values()) {
				itemCount += requests.size();
			}
			requestItemCounts.add(itemCount);
			requestTableNames.add(new ArrayList<>(request.requestItems().keySet()));

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
