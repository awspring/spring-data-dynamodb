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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SecondaryIndex;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.repository.AllowScan;
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepository;
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepositoryFactory;
import io.awspring.spring.data.dynamodb.repository.ExpressionName;
import io.awspring.spring.data.dynamodb.repository.Query;
import io.awspring.spring.data.dynamodb.repository.SecondaryIndexRepository;
import io.awspring.spring.data.dynamodb.repository.Update;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.core.support.PropertiesBasedNamedQueries;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ValueExpressionDelegate;

@DisplayName("StringBasedDynamoDbQuery")
class StringBasedDynamoDbQueryTest {

	private static final String TABLE_NAME = "widgets";
	private static final String INDEX_NAME = "gsi1";
	private static final String PK_CUST_1 = "cust-1";
	private static final String PK_CUST_2 = "cust-2";
	private static final String PK_CUST_9 = "cust-9";
	private static final String ROUND_ACTIVE = "ACTIVE";

	@Table(tableName = TABLE_NAME)
	static class Widget {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String round;
	}

	@SecondaryIndex(name = INDEX_NAME, tableName = TABLE_NAME)
	static class WidgetStatusIndex {
		@PartitionKey
		String status;
		@SortKey
		String pk;
	}

	interface WidgetStatusIndexRepository extends SecondaryIndexRepository<WidgetStatusIndex> {

		@Query(filterExpression = "status = :status", allowScan = true)
		List<WidgetStatusIndex> scanByStatus(@Param("status") String status);
	}

	interface ConsistentWidgetStatusIndexRepository extends SecondaryIndexRepository<WidgetStatusIndex> {

		@Query(keyConditionExpression = "status = :status", consistentRead = true)
		List<WidgetStatusIndex> stronglyConsistentByStatus(@Param("status") String status);
	}

	interface UpdatingWidgetStatusIndexRepository extends SecondaryIndexRepository<WidgetStatusIndex> {

		@Update(updateExpression = "SET #status = :status", names = @ExpressionName(name = "#status", value = "status"))
		void changeStatus(@Param("status") String status);
	}

	interface NamedScanRepository extends Repository<Widget, String> {

		@AllowScan
		@Query(limit = 3, names = @ExpressionName(name = "#statusAlias", value = "round"))
		List<Widget> findNamedRound(@Param("round") String round);
	}

	interface UnsafeNamedScanRepository extends Repository<Widget, String> {

		List<Widget> findByRound(String round);
	}

	interface NamedUpdateRepository extends Repository<Widget, String> {

		@Update(updateExpression = "SET #round = :round")
		void findByRound(String round);
	}

	interface WidgetRepository extends Repository<Widget, String> {

		@Query(keyConditionExpression = "pk = :pk", indexName = INDEX_NAME)
		List<Widget> rawByPk(@Param("pk") String pk);

		@Query(keyConditionExpression = "pk = :0", indexName = INDEX_NAME)
		List<Widget> rawByPositional(String pk);

		@Query(filterExpression = "#s = :round", allowScan = true)
		List<Widget> scanByStatus(@Param("round") String round);
	}

	private StringBasedDynamoDbQuery queryFor(PartTreeDynamoDbQueryReplayTest.CapturingOperations operations,
			String name, Class<?>... paramTypes) throws NoSuchMethodException {
		Method method = WidgetRepository.class.getMethod(name, paramTypes);
		RepositoryMetadata metadata = new DefaultRepositoryMetadata(WidgetRepository.class);
		ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Widget.class);
		DynamoDbQueryMethod queryMethod = new DynamoDbQueryMethod(method, metadata, projectionFactory, mappingContext);
		return new StringBasedDynamoDbQuery(queryMethod, operations, ValueExpressionDelegate.create());
	}

	private PartTreeDynamoDbQueryReplayTest.CapturingOperations operations() {
		return operations(Widget.class);
	}

	private PartTreeDynamoDbQueryReplayTest.CapturingOperations operations(Class<?> entityType) {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(entityType);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		return new PartTreeDynamoDbQueryReplayTest.CapturingOperations(converter);
	}

	@Nested
	@DisplayName("Named and positional parameter binding on the query path")
	class QueryPathBindingTests {

		@Test
		@DisplayName("keyConditionExpression with indexName produces a query request with resolved values")
		void keyConditionExpressionWithIndexNameProducesARawResolvedQueryRequest() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "rawByPk", String.class);

			// Act
			query.execute(new Object[] { PK_CUST_1 });

			// Assert
			assertAll(() -> assertNotNull(operations.lastCapturedRequest),
					() -> assertEquals(INDEX_NAME, operations.lastCapturedRequest.getIndexName()),
					() -> assertEquals("pk = :pk", operations.lastCapturedRequest.getKeyConditionExpression()),
					() -> assertEquals(PK_CUST_1,
							operations.lastCapturedRequest.getExpressionAttributeValues().get(":pk")),
					() -> assertNull(operations.lastCapturedScanRequest));
		}

		@Test
		@DisplayName("positional placeholder binds by parameter index")
		void positionalPlaceholderBindsByParameterIndex() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "rawByPositional", String.class);

			// Act
			query.execute(new Object[] { PK_CUST_9 });

			// Assert
			assertAll(() -> assertNotNull(operations.lastCapturedRequest),
					() -> assertEquals("pk = :0", operations.lastCapturedRequest.getKeyConditionExpression()),
					() -> assertEquals(PK_CUST_9,
							operations.lastCapturedRequest.getExpressionAttributeValues().get(":0")));
		}

		@Test
		@DisplayName("named binding leaves the token verbatim in the expression")
		void namedBindingLeavesTheTokenVerbatimInTheExpression() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "rawByPk", String.class);

			// Act
			query.execute(new Object[] { PK_CUST_2 });

			// Assert
			assertAll(() -> assertNotNull(operations.lastCapturedRequest),
					() -> assertFalse(operations.lastCapturedRequest.getKeyConditionExpression().contains("__spel_")),
					() -> assertEquals(PK_CUST_2,
							operations.lastCapturedRequest.getExpressionAttributeValues().get(":pk")));
		}

		@Test
		@DisplayName("named query combined with @Update is rejected at bootstrap")
		void namedQueryCombinedWithUpdateIsRejectedAtBootstrap() {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			DynamoDbRepositoryFactory factory = new DynamoDbRepositoryFactory(operations);
			Properties properties = new Properties();
			properties.setProperty("Widget.findByRound", "round = :round");
			factory.setNamedQueries(new PropertiesBasedNamedQueries(properties));

			assertThrows(InvalidDataAccessApiUsageException.class,
					() -> factory.getRepository(NamedUpdateRepository.class));
		}

		@Test
		@DisplayName("@Update on a secondary-index repository is rejected at bootstrap")
		void updateOnSecondaryIndexRepositoryIsRejectedAtBootstrap() {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations(WidgetStatusIndex.class);
			DynamoDbRepositoryFactory factory = new DynamoDbRepositoryFactory(operations);

			assertThrows(InvalidDataAccessApiUsageException.class,
					() -> factory.getRepository(UpdatingWidgetStatusIndexRepository.class));
		}

		@Test
		@DisplayName("@Query(indexName=...) on a base DynamoDbRepository is rejected at bootstrap")
		void indexNameOnBaseRepositoryRejectedAtBootstrap() throws NoSuchMethodException {
			interface WidgetCrudRepository extends DynamoDbRepository<Widget,String>{@Query(keyConditionExpression="pk = :pk",indexName=INDEX_NAME)List<Widget>byIndex(@Param("pk")String pk);}

			Method method = WidgetCrudRepository.class.getMethod("byIndex", String.class);
			RepositoryMetadata metadata = new DefaultRepositoryMetadata(WidgetCrudRepository.class);
			ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
			DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
			mappingContext.getRequiredPersistentEntity(Widget.class);

			assertThrows(InvalidDataAccessApiUsageException.class,
					() -> new DynamoDbQueryMethod(method, metadata, projectionFactory, mappingContext));
		}
	}

	@Nested
	@DisplayName("Scan path (filter expression only)")
	class ScanPathTests {

		@Test
		@DisplayName("named scan retains aliases and limit metadata")
		void namedScanRetainsAliasesAndLimitMetadata() {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			DynamoDbRepositoryFactory factory = new DynamoDbRepositoryFactory(operations);
			Properties properties = new Properties();
			properties.setProperty("Widget.findNamedRound", "#statusAlias = :round");
			factory.setNamedQueries(new PropertiesBasedNamedQueries(properties));
			NamedScanRepository repository = factory.getRepository(NamedScanRepository.class);

			repository.findNamedRound(ROUND_ACTIVE);

			assertAll(
					() -> assertEquals("#statusAlias = :round",
							operations.lastCapturedScanRequest.getFilterExpression()),
					() -> assertEquals("round",
							operations.lastCapturedScanRequest.getExpressionAttributeNames().get("#statusAlias")),
					() -> assertEquals(3, operations.lastCapturedScanRequest.getLimit()));
		}

		@Test
		@DisplayName("named scan without @AllowScan is rejected at bootstrap")
		void namedScanWithoutAllowScanIsRejectedAtBootstrap() {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			DynamoDbRepositoryFactory factory = new DynamoDbRepositoryFactory(operations);
			Properties properties = new Properties();
			properties.setProperty("Widget.findByRound", "round = :round");
			factory.setNamedQueries(new PropertiesBasedNamedQueries(properties));

			assertThrows(InvalidDataAccessApiUsageException.class,
					() -> factory.getRepository(UnsafeNamedScanRepository.class));
		}

		@Test
		@DisplayName("filter-only query on a secondary-index repository scans that index")
		void filterOnlyQueryOnSecondaryIndexRepositoryScansThatIndex() {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations(WidgetStatusIndex.class);
			DynamoDbRepositoryFactory factory = new DynamoDbRepositoryFactory(operations);
			WidgetStatusIndexRepository repository = factory.getRepository(WidgetStatusIndexRepository.class);

			// Act
			repository.scanByStatus(ROUND_ACTIVE);

			// Assert
			assertAll(() -> assertNotNull(operations.lastCapturedScanRequest),
					() -> assertEquals(INDEX_NAME, operations.lastCapturedScanRequest.getIndexName()),
					() -> assertEquals("status = :status", operations.lastCapturedScanRequest.getFilterExpression()));
		}

		@Test
		@DisplayName("consistentRead on a secondary-index view is rejected at bootstrap")
		void consistentReadOnSecondaryIndexViewIsRejectedAtBootstrap() {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations(WidgetStatusIndex.class);
			DynamoDbRepositoryFactory factory = new DynamoDbRepositoryFactory(operations);

			assertThrows(InvalidDataAccessApiUsageException.class,
					() -> factory.getRepository(ConsistentWidgetStatusIndexRepository.class));
		}

		@Test
		@DisplayName("filterExpression only produces a scan with a resolvable filter")
		void filterExpressionOnlyProducesAScanWithAResolvableFilter() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "scanByStatus", String.class);

			// Act
			query.execute(new Object[] { ROUND_ACTIVE });

			// Assert
			assertAll(() -> assertNotNull(operations.lastCapturedScanRequest),
					() -> assertEquals("#s = :round", operations.lastCapturedScanRequest.getFilterExpression()),
					() -> assertEquals(ROUND_ACTIVE,
							operations.lastCapturedScanRequest.getExpressionAttributeValues().get(":round")),
					() -> assertNull(operations.lastCapturedRequest));
		}
	}
}
