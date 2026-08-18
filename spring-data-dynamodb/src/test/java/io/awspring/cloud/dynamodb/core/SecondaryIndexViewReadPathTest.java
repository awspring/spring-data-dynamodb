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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SecondaryIndex;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

@DisplayName("DynamoDbTemplate -- @SecondaryIndex view read-path behaviour")
class SecondaryIndexViewReadPathTest {

	private static final String INDEX_NAME = "by_status";
	private static final String BASE_TABLE = "arena";

	private DynamoDbClient mockClient;
	private DynamoDbTemplate template;

	@Table(tableName = BASE_TABLE)
	static class ShopRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
	}

	@SecondaryIndex(INDEX_NAME)
	static class MatchesByRound {
		@PartitionKey
		String round;
		@SortKey
		String createdAt;
	}

	@BeforeEach
	void setUp() {
		mockClient = mock(DynamoDbClient.class);
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(ShopRow.class);
		mappingContext.getRequiredPersistentEntity(MatchesByRound.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		template = new DynamoDbTemplate(mockClient, converter);
	}

	@Test
	@DisplayName("query on a view auto-seeds its index name and resolved table name")
	void queryOnView_autoSeedsIndexAndResolvedTableName() {
		when(mockClient.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().items(List.of()).build());

		template.query(MatchesByRound.class, INDEX_NAME).partition("round", "QUARTERFINAL").execute();

		ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
		verify(mockClient).query(captor.capture());
		assertAll(() -> assertEquals(INDEX_NAME, captor.getValue().indexName()),
				() -> assertEquals(BASE_TABLE, captor.getValue().tableName()));
	}

	@Test
	@DisplayName("findAll on a view scans its own index, not the base table")
	void findAllOnView_scansOwnIndex() {
		when(mockClient.scan(any(ScanRequest.class))).thenReturn(ScanResponse.builder().items(List.of()).build());

		template.findAll(MatchesByRound.class);

		ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
		verify(mockClient).scan(captor.capture());
		assertAll(() -> assertEquals(INDEX_NAME, captor.getValue().indexName()),
				() -> assertEquals(BASE_TABLE, captor.getValue().tableName()));
	}

	@Test
	@DisplayName("save on a view is rejected as read-only")
	void saveOnView_isRejected() {
		MatchesByRound view = new MatchesByRound();
		view.round = "QUARTERFINAL";
		view.createdAt = "2026-01-01";

		InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
				() -> template.save(view));

		assertAll(() -> assertTrue(ex.getMessage().contains(INDEX_NAME)),
				() -> assertTrue(ex.getMessage().contains("read-only")));
		verifyNoInteractions(mockClient);
	}

	@Test
	@DisplayName("insert on a view is rejected")
	void insertOnView_isRejected() {
		MatchesByRound view = new MatchesByRound();
		view.round = "QUARTERFINAL";

		assertThrows(InvalidDataAccessApiUsageException.class, () -> template.insert(view));
		verifyNoInteractions(mockClient);
	}

	@Test
	@DisplayName("delete on a view is rejected")
	void deleteOnView_isRejected() {
		MatchesByRound view = new MatchesByRound();
		view.round = "QUARTERFINAL";

		assertThrows(InvalidDataAccessApiUsageException.class, () -> template.delete(view));
		verifyNoInteractions(mockClient);
	}

	@Test
	@DisplayName("findById on a view is rejected (no GetItem)")
	void findByIdOnView_isRejected() {
		InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
				() -> template.findById("QUARTERFINAL", MatchesByRound.class));

		assertTrue(ex.getMessage().contains("GetItem"));
		verifyNoInteractions(mockClient);
	}

	@Test
	@DisplayName("update on a view is rejected")
	void updateOnView_isRejected() {
		MatchesByRound view = new MatchesByRound();
		view.round = "QUARTERFINAL";

		assertThrows(InvalidDataAccessApiUsageException.class, () -> template.update(view));
		verifyNoInteractions(mockClient);
	}

	@Test
	@DisplayName("normal base-table writes and reads are unaffected")
	void baseTableReadWrite_isUnaffected() {
		when(mockClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
		when(mockClient.getItem(any(GetItemRequest.class)))
				.thenReturn(GetItemResponse.builder().item(Map.of()).build());

		ShopRow row = new ShopRow();
		row.pk = "CUSTOMER#1";
		row.sk = "MATCH#1";

		template.save(row);
		template.findById("CUSTOMER#1", ShopRow.class);
	}
}
