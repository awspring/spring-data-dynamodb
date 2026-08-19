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
package io.awspring.spring.data.dynamodb.repository.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.awspring.spring.data.dynamodb.core.DynamoDbOperations;
import io.awspring.spring.data.dynamodb.core.EntityQueryResult;
import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.AggregateItem;
import io.awspring.spring.data.dynamodb.core.mapping.AggregateTable;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.repository.AggregateRepository;
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepositoryFactory;
import io.awspring.spring.data.dynamodb.repository.ExpressionName;
import io.awspring.spring.data.dynamodb.repository.Modifying;
import io.awspring.spring.data.dynamodb.repository.Query;
import io.awspring.spring.data.dynamodb.request.DynamoDbPageRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbQueryRequest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.repository.query.Param;

@DisplayName("StringBased aggregate query (@Query on AggregateRepository)")
class StringBasedAggregateQueryTest {

	private static final String TABLE_NAME = "commerce";
	private static final String PARTITION_KEY_VALUE = "CUSTOMER#1";
	private static final String SORT_PREFIX = "ORDER#";
	private static final String STATUS_PLACED = "PLACED";
	private static final String GSI_PK_VALUE = "GSI_PK_VALUE";

	@Table(tableName = TABLE_NAME)
	static class OrderRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String status;
	}

	@Table(tableName = TABLE_NAME)
	static class LineRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String sku;
	}

	@AggregateTable(tableName = TABLE_NAME, partitionKey = "pk", sortKey = "sk")
	static class OrderAggregate {
		@AggregateItem(regex = "ORDER#[^#]+")
		OrderRow order;
		@AggregateItem(regex = "ORDER#[^#]+#LINE#[^#]+")
		List<LineRow> lines;
	}

	interface OrderAggregateRepository extends AggregateRepository<OrderAggregate> {

		@Query(keyConditionExpression = "#pk = :pk AND begins_with(#sk, :prefix)", indexName = "base", names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#sk", value = "sk") })
		Optional<OrderAggregate> loadOrders(@Param("pk") String pk, @Param("prefix") String prefix);

		@Query(keyConditionExpression = "#pk = :pk", filterExpression = "#status = :status", indexName = "base", names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#status", value = "status") })
		Optional<OrderAggregate> loadWithFilter(@Param("pk") String pk, @Param("status") String status);

		@Query(keyConditionExpression = "#pk = :pk", indexName = "GSI1", names = @ExpressionName(name = "#pk", value = "gsi1pk"))
		OrderAggregate loadFromGsi(@Param("pk") String pk);
	}

	private DynamoDbOperations operations;
	private DynamoDbRepositoryFactory factory;

	@SuppressWarnings("unchecked")
	private static <T> EntityQueryResult<T> entityQueryResult(T entity, Integer count) {
		try {
			Method of = EntityQueryResult.class.getDeclaredMethod("of", Object.class, Integer.class);
			of.setAccessible(true);
			return (EntityQueryResult<T>) of.invoke(null, entity, count);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();

		this.operations = mock(DynamoDbOperations.class);
		when(operations.getConverter()).thenReturn(converter);
		this.factory = new DynamoDbRepositoryFactory(operations);
	}

	@Nested
	@DisplayName("@Query annotated methods on aggregate repositories")
	class AnnotatedQueryTests {

		@Test
		@DisplayName("routes to queryAggregate with parameter binding")
		void queryAnnotatedMethodRoutesToQueryAggregateWithParamBinding() {
			// Arrange
			OrderAggregate mockAggregate = new OrderAggregate();
			when(operations.queryAggregate(eq(OrderAggregate.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(entityQueryResult(mockAggregate, 1));

			OrderAggregateRepository repo = factory.getRepository(OrderAggregateRepository.class);

			// Act
			Optional<OrderAggregate> result = repo.loadOrders(PARTITION_KEY_VALUE, SORT_PREFIX);

			// Assert
			assertTrue(result.isPresent());
			ArgumentCaptor<DynamoDbQueryRequest> captor = ArgumentCaptor.forClass(DynamoDbQueryRequest.class);
			verify(operations).queryAggregate(eq(OrderAggregate.class), captor.capture(),
					any(DynamoDbPageRequest.class));
			DynamoDbQueryRequest request = captor.getValue();
			assertAll(
					() -> assertEquals("#pk = :pk AND begins_with(#sk, :prefix)", request.getKeyConditionExpression()),
					() -> assertEquals("pk", request.getExpressionAttributeNames().get("#pk")),
					() -> assertEquals("sk", request.getExpressionAttributeNames().get("#sk")),
					() -> assertEquals(PARTITION_KEY_VALUE, request.getExpressionAttributeValues().get(":pk")),
					() -> assertEquals(SORT_PREFIX, request.getExpressionAttributeValues().get(":prefix")));
		}

		@Test
		@DisplayName("filter expression is carried through")
		void filterExpressionIsCarriedThrough() {
			// Arrange
			when(operations.queryAggregate(eq(OrderAggregate.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(entityQueryResult(new OrderAggregate(), 1));
			OrderAggregateRepository repo = factory.getRepository(OrderAggregateRepository.class);

			// Act
			repo.loadWithFilter(PARTITION_KEY_VALUE, STATUS_PLACED);

			// Assert
			ArgumentCaptor<DynamoDbQueryRequest> captor = ArgumentCaptor.forClass(DynamoDbQueryRequest.class);
			verify(operations).queryAggregate(eq(OrderAggregate.class), captor.capture(),
					any(DynamoDbPageRequest.class));
			DynamoDbQueryRequest request = captor.getValue();
			assertAll(
					() -> assertEquals("#status = :status", request.getFilterExpression()),
					() -> assertEquals(STATUS_PLACED,
							request.getExpressionAttributeValues().get(":status")));
		}

		@Test
		@DisplayName("index name override is applied")
		void indexNameOverrideIsApplied() {
			// Arrange
			when(operations.queryAggregate(eq(OrderAggregate.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(entityQueryResult(new OrderAggregate(), 1));
			OrderAggregateRepository repo = factory.getRepository(OrderAggregateRepository.class);

			// Act
			repo.loadFromGsi(GSI_PK_VALUE);

			// Assert
			ArgumentCaptor<DynamoDbQueryRequest> captor = ArgumentCaptor.forClass(DynamoDbQueryRequest.class);
			verify(operations).queryAggregate(eq(OrderAggregate.class), captor.capture(),
					any(DynamoDbPageRequest.class));
			DynamoDbQueryRequest request = captor.getValue();
			assertAll(
					() -> assertEquals("GSI1", request.getIndexName()),
					() -> assertEquals("gsi1pk", request.getExpressionAttributeNames().get("#pk")));
		}
	}

	@Nested
	@DisplayName("Repository bootstrap and return type handling")
	class BootstrapAndReturnTests {

		@Test
		@DisplayName("non-annotated non-base method fails fast on aggregate repo")
		void nonAnnotatedNonBaseMethodFailsFastOnAggregateRepo() {
			interface BadRepo extends AggregateRepository<OrderAggregate>{OrderAggregate findByNothing(String something);}

			assertThrows(InvalidDataAccessApiUsageException.class, () -> factory.getRepository(BadRepo.class));
		}

		@Test
		@DisplayName("@Modifying on an aggregate repository is rejected at bootstrap")
		void modifyingMethodFailsFastOnAggregateRepo() {
			interface ModifyingAggregateRepo extends AggregateRepository<OrderAggregate>{@Modifying @Query(updateExpression="SET #status = :status",names=@ExpressionName(name="#status",value="status"))void touch(@Param("pk")String pk,@Param("sk")String sk,@Param("status")String status);}

			assertThrows(InvalidDataAccessApiUsageException.class,
					() -> factory.getRepository(ModifyingAggregateRepo.class));
		}

		@Test
		@DisplayName("fixed base method is still served by base class")
		void fixedBaseMethodStillServedByBaseClass() {
			// Arrange
			when(operations.queryAggregate(eq(OrderAggregate.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(entityQueryResult(new OrderAggregate(), 1));
			OrderAggregateRepository repo = factory.getRepository(OrderAggregateRepository.class);

			// Act
			Optional<OrderAggregate> result = repo.findByPartitionKey("PK#1");

			// Assert
			assertTrue(result.isPresent());
			ArgumentCaptor<DynamoDbQueryRequest> captor = ArgumentCaptor.forClass(DynamoDbQueryRequest.class);
			verify(operations).queryAggregate(eq(OrderAggregate.class), captor.capture(),
					any(DynamoDbPageRequest.class));
			assertEquals("#pk = :pk", captor.getValue().getKeyConditionExpression());
		}

		@Test
		@DisplayName("raw return type unwraps without Optional")
		void rawReturnTypeUnwrapsWithoutOptional() {
			// Arrange
			OrderAggregate mockAggregate = new OrderAggregate();
			when(operations.queryAggregate(eq(OrderAggregate.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(entityQueryResult(mockAggregate, 1));
			OrderAggregateRepository repo = factory.getRepository(OrderAggregateRepository.class);

			// Act
			OrderAggregate result = repo.loadFromGsi("PK");

			// Assert
			assertNotNull(result);
		}

		@Test
		@DisplayName("empty result throws for raw return type")
		void emptyResultThrowsForRawReturnType() {
			// Arrange
			when(operations.queryAggregate(eq(OrderAggregate.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(null);
			OrderAggregateRepository repo = factory.getRepository(OrderAggregateRepository.class);

			// Act & Assert
			assertThrows(org.springframework.dao.EmptyResultDataAccessException.class,
					() -> repo.loadFromGsi("PK"));
		}
	}
}
