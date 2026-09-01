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
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.ItemCollectionMember;
import io.awspring.spring.data.dynamodb.core.mapping.ItemCollectionView;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepositoryFactory;
import io.awspring.spring.data.dynamodb.repository.ExpressionName;
import io.awspring.spring.data.dynamodb.repository.ItemCollectionRepository;
import io.awspring.spring.data.dynamodb.repository.Query;
import io.awspring.spring.data.dynamodb.repository.Update;
import io.awspring.spring.data.dynamodb.request.DynamoDbPageRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbQueryRequest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.repository.core.support.PropertiesBasedNamedQueries;
import org.springframework.data.repository.query.Param;

@DisplayName("StringBased view query (@Query on ItemCollectionRepository)")
class StringBasedItemCollectionQueryTest {

	private static final String TABLE_NAME = "commerce";
	private static final String PARTITION_KEY_VALUE = "CUSTOMER#1";
	private static final String SORT_PREFIX = "ORDER#";
	private static final String STATUS_PLACED = "PLACED";
	private static final String GSI_PK_VALUE = "GSI_PK_VALUE";
	private static final String NAMED_QUERY_NAME = "OrderItemCollection.findNamed";

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

	@ItemCollectionView(tableName = TABLE_NAME, partitionKey = "pk", sortKey = "sk")
	static class OrderItemCollection {
		@ItemCollectionMember(regex = "ORDER#[^#]+")
		OrderRow order;
		@ItemCollectionMember(regex = "ORDER#[^#]+#LINE#[^#]+")
		List<LineRow> lines;
	}

	@ItemCollectionView(tableName = TABLE_NAME, indexName = "GSI1", partitionKey = "gsi1pk", sortKey = "gsi1sk")
	static class GsiOrderItemCollection {
		@ItemCollectionMember(regex = "ORDER#[^#]+")
		OrderRow order;
	}

	interface ConsistentGsiItemCollectionRepository extends ItemCollectionRepository<GsiOrderItemCollection> {

		@Query(keyConditionExpression = "#pk = :pk", consistentRead = true, names = @ExpressionName(name = "#pk", value = "gsi1pk"))
		Optional<GsiOrderItemCollection> load(@Param("pk") String pk);
	}

	interface PartiQlItemCollectionRepository extends ItemCollectionRepository<OrderItemCollection> {

		@Query(partiQl = "SELECT * FROM commerce WHERE pk = ?")
		Optional<OrderItemCollection> load(String pk);
	}

	interface OrderItemCollectionRepository extends ItemCollectionRepository<OrderItemCollection> {

		@Query(keyConditionExpression = "#pk = :pk AND begins_with(#sk, :prefix)", indexName = "base", names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#sk", value = "sk") })
		Optional<OrderItemCollection> loadOrders(@Param("pk") String pk, @Param("prefix") String prefix);

		@Query(keyConditionExpression = "#pk = :pk", filterExpression = "#status = :status", indexName = "base", names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#status", value = "status") })
		Optional<OrderItemCollection> loadWithFilter(@Param("pk") String pk, @Param("status") String status);

		@Query(keyConditionExpression = "#pk = :pk", indexName = "GSI1", names = @ExpressionName(name = "#pk", value = "gsi1pk"))
		OrderItemCollection loadFromGsi(@Param("pk") String pk);

		@Query(keyConditionExpression = "#pk = :pk", limit = 5, names = @ExpressionName(name = "#pk", value = "pk"))
		Optional<OrderItemCollection> loadLimited(@Param("pk") String pk);
	}

	interface NamedOrderItemCollectionRepository extends ItemCollectionRepository<OrderItemCollection> {

		@Query(limit = 4, names = @ExpressionName(name = "#partition", value = "pk"))
		Optional<OrderItemCollection> findNamed(@Param("pk") String pk, @Param("prefix") String prefix);
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
	@DisplayName("@Query annotated methods on view repositories")
	class AnnotatedQueryTests {

		@Test
		@DisplayName("routes to queryItemCollection with parameter binding")
		void queryAnnotatedMethodRoutesToQueryItemCollectionWithParamBinding() {
			// Arrange
			OrderItemCollection mockItemCollection = new OrderItemCollection();
			when(operations.queryItemCollection(eq(OrderItemCollection.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(entityQueryResult(mockItemCollection, 1));

			OrderItemCollectionRepository repo = factory.getRepository(OrderItemCollectionRepository.class);

			// Act
			Optional<OrderItemCollection> result = repo.loadOrders(PARTITION_KEY_VALUE, SORT_PREFIX);

			// Assert
			assertTrue(result.isPresent());
			ArgumentCaptor<DynamoDbQueryRequest> captor = ArgumentCaptor.forClass(DynamoDbQueryRequest.class);
			verify(operations).queryItemCollection(eq(OrderItemCollection.class), captor.capture(),
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
			when(operations.queryItemCollection(eq(OrderItemCollection.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(entityQueryResult(new OrderItemCollection(), 1));
			OrderItemCollectionRepository repo = factory.getRepository(OrderItemCollectionRepository.class);

			// Act
			repo.loadWithFilter(PARTITION_KEY_VALUE, STATUS_PLACED);

			// Assert
			ArgumentCaptor<DynamoDbQueryRequest> captor = ArgumentCaptor.forClass(DynamoDbQueryRequest.class);
			verify(operations).queryItemCollection(eq(OrderItemCollection.class), captor.capture(),
					any(DynamoDbPageRequest.class));
			DynamoDbQueryRequest request = captor.getValue();
			assertAll(
					() -> assertEquals("#status = :status", request.getFilterExpression()),
					() -> assertEquals(STATUS_PLACED,
							request.getExpressionAttributeValues().get(":status")));
		}

		@Test
		@DisplayName("query limit is forwarded as the DynamoDB page size")
		void queryLimitIsForwardedAsPageSize() {
			// Arrange
			when(operations.queryItemCollection(eq(OrderItemCollection.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(entityQueryResult(new OrderItemCollection(), 1));
			OrderItemCollectionRepository repo = factory.getRepository(OrderItemCollectionRepository.class);

			// Act
			repo.loadLimited(PARTITION_KEY_VALUE);

			// Assert
			ArgumentCaptor<DynamoDbPageRequest> captor = ArgumentCaptor.forClass(DynamoDbPageRequest.class);
			verify(operations).queryItemCollection(eq(OrderItemCollection.class), any(DynamoDbQueryRequest.class),
					captor.capture());
			assertEquals(5, captor.getValue().getLimit());
		}

		@Test
		@DisplayName("named item-collection query resolves before derived-query rejection")
		void namedItemCollectionQueryResolvesAndBindsMetadata() {
			// Arrange
			Properties properties = new Properties();
			properties.setProperty(NAMED_QUERY_NAME, "#partition = :pk AND begins_with(#sk, :prefix)");
			factory.setNamedQueries(new PropertiesBasedNamedQueries(properties));
			when(operations.queryItemCollection(eq(OrderItemCollection.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(entityQueryResult(new OrderItemCollection(), 1));
			NamedOrderItemCollectionRepository repo = factory.getRepository(NamedOrderItemCollectionRepository.class);

			// Act
			repo.findNamed(PARTITION_KEY_VALUE, SORT_PREFIX);

			// Assert
			ArgumentCaptor<DynamoDbQueryRequest> requestCaptor = ArgumentCaptor.forClass(DynamoDbQueryRequest.class);
			ArgumentCaptor<DynamoDbPageRequest> pageCaptor = ArgumentCaptor.forClass(DynamoDbPageRequest.class);
			verify(operations).queryItemCollection(eq(OrderItemCollection.class), requestCaptor.capture(),
					pageCaptor.capture());
			DynamoDbQueryRequest request = requestCaptor.getValue();
			assertAll(
					() -> assertEquals("#partition = :pk AND begins_with(#sk, :prefix)",
							request.getKeyConditionExpression()),
					() -> assertEquals("pk", request.getExpressionAttributeNames().get("#partition")),
					() -> assertEquals("sk", request.getExpressionAttributeNames().get("#sk")),
					() -> assertEquals(PARTITION_KEY_VALUE, request.getExpressionAttributeValues().get(":pk")),
					() -> assertEquals(SORT_PREFIX, request.getExpressionAttributeValues().get(":prefix")),
					() -> assertEquals(4, pageCaptor.getValue().getLimit()));
		}

		@Test
		@DisplayName("index name override is applied")
		void indexNameOverrideIsApplied() {
			// Arrange
			when(operations.queryItemCollection(eq(OrderItemCollection.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(entityQueryResult(new OrderItemCollection(), 1));
			OrderItemCollectionRepository repo = factory.getRepository(OrderItemCollectionRepository.class);

			// Act
			repo.loadFromGsi(GSI_PK_VALUE);

			// Assert
			ArgumentCaptor<DynamoDbQueryRequest> captor = ArgumentCaptor.forClass(DynamoDbQueryRequest.class);
			verify(operations).queryItemCollection(eq(OrderItemCollection.class), captor.capture(),
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
		@DisplayName("non-annotated non-base method fails fast on view repo")
		void nonAnnotatedNonBaseMethodFailsFastOnItemCollectionRepo() {
			interface BadRepo extends ItemCollectionRepository<OrderItemCollection>{OrderItemCollection findByNothing(String something);}

			assertThrows(InvalidDataAccessApiUsageException.class, () -> factory.getRepository(BadRepo.class));
		}

		@Test
		@DisplayName("@Update on an view repository is rejected at bootstrap")
		void updateMethodFailsFastOnItemCollectionRepo() {
			interface UpdateItemCollectionRepo extends ItemCollectionRepository<OrderItemCollection>{@Update(updateExpression="SET #status = :status",names=@ExpressionName(name="#status",value="status"))void touch(@Param("pk")String pk,@Param("sk")String sk,@Param("status")String status);}

			assertThrows(InvalidDataAccessApiUsageException.class,
					() -> factory.getRepository(UpdateItemCollectionRepo.class));
		}

		@Test
		@DisplayName("entity-level GSI rejects consistentRead at bootstrap")
		void entityLevelGsiConsistentReadFailsFast() {
			InvalidDataAccessApiUsageException exception = assertThrows(InvalidDataAccessApiUsageException.class,
					() -> factory.getRepository(ConsistentGsiItemCollectionRepository.class));

			assertTrue(exception.getMessage().contains("eventually consistent"));
		}

		@Test
		@DisplayName("PartiQL is rejected on item-collection repositories at bootstrap")
		void partiQlFailsFastOnItemCollectionRepository() {
			InvalidDataAccessApiUsageException exception = assertThrows(InvalidDataAccessApiUsageException.class,
					() -> factory.getRepository(PartiQlItemCollectionRepository.class));

			assertTrue(exception.getMessage().contains("partiQl"));
		}

		@Test
		@DisplayName("fixed base method is still served by base class")
		void fixedBaseMethodStillServedByBaseClass() {
			// Arrange
			when(operations.queryItemCollection(eq(OrderItemCollection.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(entityQueryResult(new OrderItemCollection(), 1));
			OrderItemCollectionRepository repo = factory.getRepository(OrderItemCollectionRepository.class);

			// Act
			Optional<OrderItemCollection> result = repo.findByPartitionKey("PK#1");

			// Assert
			assertTrue(result.isPresent());
			ArgumentCaptor<DynamoDbQueryRequest> captor = ArgumentCaptor.forClass(DynamoDbQueryRequest.class);
			verify(operations).queryItemCollection(eq(OrderItemCollection.class), captor.capture(),
					any(DynamoDbPageRequest.class));
			assertEquals("#pk = :pk", captor.getValue().getKeyConditionExpression());
		}

		@Test
		@DisplayName("raw return type unwraps without Optional")
		void rawReturnTypeUnwrapsWithoutOptional() {
			// Arrange
			OrderItemCollection mockItemCollection = new OrderItemCollection();
			when(operations.queryItemCollection(eq(OrderItemCollection.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(entityQueryResult(mockItemCollection, 1));
			OrderItemCollectionRepository repo = factory.getRepository(OrderItemCollectionRepository.class);

			// Act
			OrderItemCollection result = repo.loadFromGsi("PK");

			// Assert
			assertNotNull(result);
		}

		@Test
		@DisplayName("empty result throws for raw return type")
		void emptyResultThrowsForRawReturnType() {
			// Arrange
			when(operations.queryItemCollection(eq(OrderItemCollection.class), any(DynamoDbQueryRequest.class),
					any(DynamoDbPageRequest.class))).thenReturn(null);
			OrderItemCollectionRepository repo = factory.getRepository(OrderItemCollectionRepository.class);

			// Act & Assert
			assertThrows(org.springframework.dao.EmptyResultDataAccessException.class,
					() -> repo.loadFromGsi("PK"));
		}
	}
}
