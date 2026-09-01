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
package io.awspring.spring.data.dynamodb.core;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.ItemCollectionMember;
import io.awspring.spring.data.dynamodb.core.mapping.ItemCollectionView;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.request.DynamoDbPageRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbQueryRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

@DisplayName("DynamoDbTemplate item-collection behavior")
class ItemCollectionTemplateTest {

	private static final String TABLE_NAME = "commerce";
	private static final String PARTITION_KEY_VALUE = "CUSTOMER#1";

	private DynamoDbClient client;
	private DynamoDbTemplate template;

	@Table(tableName = TABLE_NAME)
	static class OrderRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
	}

	@ItemCollectionView(tableName = TABLE_NAME, partitionKey = "pk", sortKey = "sk")
	static class Orders {
		@ItemCollectionMember(regex = "ORDER#[^#]+")
		List<OrderRow> rows;
	}

	@BeforeEach
	void setUp() {
		client = mock(DynamoDbClient.class);
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(OrderRow.class);
		mappingContext.getRequiredPersistentEntity(Orders.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		template = new DynamoDbTemplate(client, converter);
	}

	@Test
	@DisplayName("queryItemCollection returns one page and a cursor for caller-controlled continuation")
	void queryItemCollectionReturnsOnePageAndCursorForCallerContinuation() {
		Map<String, AttributeValue> initialCursor = item("CUSTOMER#0", "ORDER#0");
		Map<String, AttributeValue> nextCursor = item(PARTITION_KEY_VALUE, "ORDER#1");
		Map<String, Object> nextPageCursor = Map.of("pk", PARTITION_KEY_VALUE, "sk", "ORDER#1");
		when(client.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder()
				.items(item(PARTITION_KEY_VALUE, "ORDER#1")).lastEvaluatedKey(nextCursor).build(),
				QueryResponse.builder().items(item(PARTITION_KEY_VALUE, "ORDER#2")).build());
		DynamoDbQueryRequest request = DynamoDbQueryRequest.request().withKeyConditionExpression("#pk = :pk")
				.withExpressionAttributeNames(Map.of("#pk", "pk"))
				.withExpressionAttributeValues(Map.of(":pk", PARTITION_KEY_VALUE)).build();

		EntityQueryResult<Orders> first = template.queryItemCollection(Orders.class, request,
				DynamoDbPageRequest.of(7, Map.of("pk", "CUSTOMER#0", "sk", "ORDER#0")));
		EntityQueryResult<Orders> second = template.queryItemCollection(Orders.class, request,
				DynamoDbPageRequest.of(7, first.getLastEvaluatedKey()));

		ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
		verify(client, org.mockito.Mockito.times(2)).query(captor.capture());
		List<QueryRequest> requests = captor.getAllValues();
		assertAll(() -> assertEquals(List.of("ORDER#1"), first.getEntity().rows.stream().map(row -> row.sk).toList()),
				() -> assertEquals(nextPageCursor, first.getLastEvaluatedKey()),
				() -> assertEquals(List.of("ORDER#2"), second.getEntity().rows.stream().map(row -> row.sk).toList()),
				() -> assertEquals(null, second.getLastEvaluatedKey()), () -> assertEquals(7, requests.get(0).limit()),
				() -> assertEquals(7, requests.get(1).limit()),
				() -> assertEquals(initialCursor, requests.get(0).exclusiveStartKey()),
				() -> assertEquals(nextCursor, requests.get(1).exclusiveStartKey()));
	}

	@Test
	@DisplayName("item-collection views reject point reads and every write operation")
	void itemCollectionViewsAreReadOnly() {
		Orders view = new Orders();

		assertAll(() -> assertThrows(InvalidDataAccessApiUsageException.class, () -> template.save(view)),
				() -> assertThrows(InvalidDataAccessApiUsageException.class, () -> template.insert(view)),
				() -> assertThrows(InvalidDataAccessApiUsageException.class, () -> template.update(view)),
				() -> assertThrows(InvalidDataAccessApiUsageException.class, () -> template.delete(view)),
				() -> assertThrows(InvalidDataAccessApiUsageException.class,
						() -> template.findById(PARTITION_KEY_VALUE, Orders.class)));
		verifyNoMoreInteractions(client);
	}

	private static Map<String, AttributeValue> item(String pk, String sk) {
		return Map.of("pk", AttributeValue.builder().s(pk).build(), "sk", AttributeValue.builder().s(sk).build());
	}
}
