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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

class SecondaryIndexViewReadPathTest {

	private DynamoDbClient mockClient;
	private DynamoDbTemplate template;

	@Table(tableName = "arena")
	static class ShopRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
	}

	@SecondaryIndex("by_status")
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
	void queryOnAViewAutoSeedsItsIndexNameAndResolvedTableName() {
		when(mockClient.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().items(List.of()).build());

		template.query(MatchesByRound.class, "by_status").partition("round", "QUARTERFINAL").execute();

		ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
		org.mockito.Mockito.verify(mockClient).query(captor.capture());
		assertThat(captor.getValue().indexName()).isEqualTo("by_status");
		assertThat(captor.getValue().tableName()).isEqualTo("arena");
	}

	@Test
	void findAllOnAViewScansItsOwnIndexNotTheBaseTable() {
		when(mockClient.scan(any(ScanRequest.class))).thenReturn(ScanResponse.builder().items(List.of()).build());

		template.findAll(MatchesByRound.class);

		ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
		org.mockito.Mockito.verify(mockClient).scan(captor.capture());
		assertThat(captor.getValue().indexName()).isEqualTo("by_status");
		assertThat(captor.getValue().tableName()).isEqualTo("arena");
	}

	@Test
	void saveOnAViewIsRejected() {
		MatchesByRound view = new MatchesByRound();
		view.round = "QUARTERFINAL";
		view.createdAt = "2026-01-01";

		assertThatThrownBy(() -> template.save(view)).isInstanceOf(InvalidDataAccessApiUsageException.class)
				.hasMessageContaining("by_status").hasMessageContaining("read-only");
		org.mockito.Mockito.verifyNoInteractions(mockClient);
	}

	@Test
	void insertOnAViewIsRejected() {
		MatchesByRound view = new MatchesByRound();
		view.round = "QUARTERFINAL";

		assertThatThrownBy(() -> template.insert(view)).isInstanceOf(InvalidDataAccessApiUsageException.class);
		org.mockito.Mockito.verifyNoInteractions(mockClient);
	}

	@Test
	void deleteOnAViewIsRejected() {
		MatchesByRound view = new MatchesByRound();
		view.round = "QUARTERFINAL";

		assertThatThrownBy(() -> template.delete(view)).isInstanceOf(InvalidDataAccessApiUsageException.class);
		org.mockito.Mockito.verifyNoInteractions(mockClient);
	}

	@Test
	void findByIdOnAViewIsRejected() {
		assertThatThrownBy(() -> template.findById("QUARTERFINAL", MatchesByRound.class))
				.isInstanceOf(InvalidDataAccessApiUsageException.class).hasMessageContaining("GetItem");
		org.mockito.Mockito.verifyNoInteractions(mockClient);
	}

	@Test
	void updateOnAViewIsRejected() {
		MatchesByRound view = new MatchesByRound();
		view.round = "QUARTERFINAL";

		assertThatThrownBy(() -> template.update(view)).isInstanceOf(InvalidDataAccessApiUsageException.class);
		org.mockito.Mockito.verifyNoInteractions(mockClient);
	}

	@Test
	void normalBaseTableWritesAndReadsAreUnaffected() {
		when(mockClient.putItem(any(software.amazon.awssdk.services.dynamodb.model.PutItemRequest.class)))
				.thenReturn(software.amazon.awssdk.services.dynamodb.model.PutItemResponse.builder().build());
		when(mockClient.getItem(any(GetItemRequest.class)))
				.thenReturn(GetItemResponse.builder().item(Map.of()).build());

		ShopRow row = new ShopRow();
		row.pk = "CUSTOMER#1";
		row.sk = "MATCH#1";

		template.save(row);
		template.findById("CUSTOMER#1", ShopRow.class);
	}
}
