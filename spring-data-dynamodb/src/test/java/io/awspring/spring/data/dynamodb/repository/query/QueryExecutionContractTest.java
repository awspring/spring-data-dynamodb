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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.repository.ExpressionName;
import io.awspring.spring.data.dynamodb.repository.Query;
import io.awspring.spring.data.dynamodb.repository.Update;
import io.awspring.spring.data.dynamodb.request.DynamoDbQueryRequest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ValueExpressionDelegate;

@DisplayName("Query execution contract")
class QueryExecutionContractTest {

	private static final String TABLE_NAME = "tournament_arena";
	private static final String PK_P1 = "P1";
	private static final String SK_M1 = "M#1";
	private static final String WINNER = "player-7";

	@Table(tableName = TABLE_NAME)
	static class Match {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String region;
		String winner;
	}

	interface MatchRepository extends Repository<Match, String> {

		Window<Match> findWindowByPk(String pk, ScrollPosition position, Limit limit);

		long countByPk(String pk);

		List<Match> findByPk(String pk);

		@Update(updateExpression = "SET #winner = :winner", names = @ExpressionName(name = "#winner", value = "winner"))
		void recordWinnerReturningNothing(@Param("pk") String pk, @Param("sk") String sk,
				@Param("winner") String winner);

		@Update(updateExpression = "SET #winner = :winner", names = @ExpressionName(name = "#winner", value = "winner"))
		boolean recordWinnerReturningBoolean(@Param("pk") String pk, @Param("sk") String sk,
				@Param("winner") String winner);

		@Update(updateExpression = "SET #winner = :winner", names = @ExpressionName(name = "#winner", value = "winner"))
		int recordWinnerReturningCount(@Param("pk") String pk, @Param("sk") String sk, @Param("winner") String winner);

		@Update(updateExpression = "SET #winner = :winner", names = @ExpressionName(name = "#winner", value = "winner"))
		Match recordWinnerReturningEntity(@Param("pk") String pk, @Param("sk") String sk,
				@Param("winner") String winner);
	}

	interface RejectedRepository extends Repository<Match, String> {

		@Query(keyConditionExpression = "#pk = :pk", indexName = "GSI1", limit = 0, names = @ExpressionName(name = "#pk", value = "pk"))
		List<Match> findWithZeroLimit(@Param("pk") String pk);

		@Query(keyConditionExpression = "#pk = :pk", indexName = "GSI1", consistentRead = true, names = @ExpressionName(name = "#pk", value = "pk"))
		List<Match> findConsistentlyOnGsi(@Param("pk") String pk);

		List<Match> deleteByPk(String pk);

		List<Match> findDistinctByPk(String pk);

		List<Match> findByRegionIgnoreCase(String region);

		List<Match> findTop2ByPk(String pk, Limit limit);

		@Update(updateExpression = "SET #winner = :winner", names = @ExpressionName(name = "#winner", value = "winner"))
		String recordWinnerReturningNonsense(@Param("pk") String pk, @Param("sk") String sk,
				@Param("winner") String winner);

		@Update(updateExpression = "SET #winner = :winner", names = @ExpressionName(name = "#winner", value = "winner"))
		@Query(filterExpression = "#winner = :winner", allowScan = true)
		void queryAndUpdate(@Param("pk") String pk, @Param("sk") String sk, @Param("winner") String winner);
	}

	private PartTreeDynamoDbQueryReplayTest.CapturingOperations operations() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		return new PartTreeDynamoDbQueryReplayTest.CapturingOperations(converter);
	}

	private PartTreeDynamoDbQuery partTreeQuery(PartTreeDynamoDbQueryReplayTest.CapturingOperations operations,
			String name, Class<?>... paramTypes) throws NoSuchMethodException {
		return new PartTreeDynamoDbQuery(queryMethod(MatchRepository.class, name, paramTypes), operations);
	}

	private StringBasedDynamoDbQuery stringQuery(PartTreeDynamoDbQueryReplayTest.CapturingOperations operations,
			Class<?> repositoryInterface, String name, Class<?>... paramTypes) throws NoSuchMethodException {
		return new StringBasedDynamoDbQuery(queryMethod(repositoryInterface, name, paramTypes), operations,
				ValueExpressionDelegate.create());
	}

	private DynamoDbQueryMethod queryMethod(Class<?> repositoryInterface, String name, Class<?>... paramTypes)
			throws NoSuchMethodException {
		Method method = repositoryInterface.getMethod(name, paramTypes);
		RepositoryMetadata metadata = new DefaultRepositoryMetadata(repositoryInterface);
		ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		return new DynamoDbQueryMethod(method, metadata, projectionFactory, mappingContext);
	}

	@Nested
	@DisplayName("Window scroll position handling")
	class ScrollPositionTests {

		@Test
		@DisplayName("a backward keyset position is rejected rather than paginating forward")
		void aBackwardKeysetPositionIsRejectedRatherThanPaginatingForward() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			PartTreeDynamoDbQuery query = partTreeQuery(operations, "findWindowByPk", String.class,
					ScrollPosition.class, Limit.class);

			Map<String, Object> cursor = Map.of("pk", PK_P1, "sk", SK_M1);
			InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
					() -> query.execute(new Object[] { PK_P1, ScrollPosition.backward(cursor), Limit.of(10) }));

			assertAll(
					() -> assertTrue(ex.getMessage().contains("forward"),
							"the message must point at the supported direction"),
					() -> assertNull(operations.lastCapturedRequest,
							"a rejected position must not reach DynamoDB at all"));
		}

		@Test
		@DisplayName("a forward keyset position still becomes the exclusive start key")
		void aForwardKeysetPositionStillBecomesTheExclusiveStartKey() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			PartTreeDynamoDbQuery query = partTreeQuery(operations, "findWindowByPk", String.class,
					ScrollPosition.class, Limit.class);

			Map<String, Object> cursor = Map.of("pk", PK_P1, "sk", SK_M1);
			query.execute(new Object[] { PK_P1, ScrollPosition.forward(cursor), Limit.of(10) });

			assertAll(() -> assertNotNull(operations.lastCapturedPageRequest),
					() -> assertEquals(cursor, operations.lastCapturedPageRequest.getLastEvaluatedKey()));
		}
	}

	@Nested
	@DisplayName("Count delegation")
	class CountTests {

		@Test
		@DisplayName("count is delegated to operations.count and ignores any inbound cursor")
		void countIsDelegatedToTheOperationsCountAndIgnoresAnyInboundCursor() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			operations.scriptedCount = 7L;
			PartTreeDynamoDbQuery query = partTreeQuery(operations, "countByPk", String.class);

			assertAll(() -> assertEquals(7L, query.execute(new Object[] { PK_P1 })),
					() -> assertTrue(operations.countedViaQuery,
							"a count must not be assembled from materialised items"),
					() -> assertNotNull(operations.lastCapturedRequest),
					() -> assertNull(operations.lastCapturedPageRequest,
							"counting pages is the template's job, so no page request is issued here"));
		}
	}

	@Nested
	@DisplayName("@Update return types")
	class UpdateReturnTypeTests {

		@Test
		@DisplayName("return types follow the Spring Data JDBC convention")
		void updateReturnTypesFollowTheSpringDataJdbcConvention() throws NoSuchMethodException {
			Match updated = new Match();
			updated.pk = PK_P1;

			assertAll(() -> assertNull(executeUpdate("recordWinnerReturningNothing", updated), "void reports nothing"),
					() -> assertEquals(Boolean.TRUE, executeUpdate("recordWinnerReturningBoolean", updated),
							"boolean reports that the update applied"),
					() -> assertEquals(1, executeUpdate("recordWinnerReturningCount", updated),
							"int reports the single affected item"),
					() -> assertEquals(updated, executeUpdate("recordWinnerReturningEntity", updated),
							"an entity return type hands back the updated item"));
		}

		@Test
		@DisplayName("an update method still reaches the update with its resolved keys")
		void anUpdateMethodStillReachesTheUpdateWithItsResolvedKeys() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			operations.scriptedUpdatedEntity = new Match();
			StringBasedDynamoDbQuery query = stringQuery(operations, MatchRepository.class,
					"recordWinnerReturningBoolean", String.class, String.class, String.class);

			query.execute(new Object[] { PK_TOURNAMENT, "MATCH#m1", WINNER });

			assertAll(() -> assertEquals(PK_TOURNAMENT, operations.lastUpdatePartitionKey),
					() -> assertEquals("MATCH#m1", operations.lastUpdateSortKey),
					() -> assertNotNull(operations.lastCapturedUpdateRequest),
					() -> assertEquals("SET #winner = :winner",
							operations.lastCapturedUpdateRequest.getUpdateExpression()));
		}
	}

	@Nested
	@DisplayName("Bootstrap rejections")
	class BootstrapRejectionTests {

		@Test
		@DisplayName("a zero @Query limit is rejected at bootstrap")
		void aZeroQueryLimitIsRejectedAtBootstrap() {
			InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
					() -> queryMethod(RejectedRepository.class, "findWithZeroLimit", String.class));
			assertTrue(ex.getMessage().contains("limit"), "the message must name the offending attribute");
		}

		@Test
		@DisplayName("a strongly consistent GSI query is rejected at bootstrap")
		void aStronglyConsistentGsiQueryIsRejectedAtBootstrap() {
			InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
					() -> queryMethod(RejectedRepository.class, "findConsistentlyOnGsi", String.class));
			assertTrue(ex.getMessage().contains("eventually consistent"));
		}

		@Test
		@DisplayName("unsupported derived query keywords are rejected at bootstrap")
		void unsupportedDerivedQueryKeywordsAreRejectedAtBootstrap() {
			assertDerivedKeywordRejected("deleteByPk", "Delete");
			assertDerivedKeywordRejected("findDistinctByPk", "Distinct");
			assertDerivedKeywordRejected("findByRegionIgnoreCase", "IgnoreCase");
		}

		private void assertDerivedKeywordRejected(String methodName, String expectedMessage) {
			InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class, () -> {
				DynamoDbQueryMethod method = queryMethod(RejectedRepository.class, methodName, String.class);
				new PartTreeDynamoDbQuery(method, operations());
			});
			assertTrue(ex.getMessage().contains(expectedMessage));
		}

		@Test
		@DisplayName("a derived Top limit combined with a Limit parameter is rejected at bootstrap")
		void aDerivedTopLimitCombinedWithALimitParameterIsRejectedAtBootstrap() {
			InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
					() -> queryMethod(RejectedRepository.class, "findTop2ByPk", String.class, Limit.class));
			assertTrue(ex.getMessage().contains("Top"), "the message must name the competing keyword");
		}

		@Test
		@DisplayName("an update method with an unsupported return type is rejected at bootstrap")
		void anUpdateMethodWithAnUnsupportedReturnTypeIsRejectedAtBootstrap() {
			InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
					() -> queryMethod(RejectedRepository.class, "recordWinnerReturningNonsense", String.class,
							String.class, String.class));
			assertTrue(ex.getMessage().contains("@Update"), "the message must name the annotation at fault");
		}

		@Test
		@DisplayName("@Query and @Update are rejected on the same method")
		void queryAndUpdateAreMutuallyExclusive() {
			InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
					() -> queryMethod(RejectedRepository.class, "queryAndUpdate", String.class, String.class,
							String.class));
			assertTrue(ex.getMessage().contains("mutually exclusive"));
		}
	}

	@Nested
	@DisplayName("DynamoDbQuerySpecMapper")
	class SpecMapperTests {

		@Test
		@DisplayName("a template sort-key request joins every key conjunct with AND")
		void aTemplateSortKeyRequestJoinsEveryKeyConjunctWithAnd() {
			DynamoDbQuerySpec spec = DynamoDbQuerySpec.forIndex("GSI1");
			spec.partitionEquals().put("gsi1pk", "PT#winter2026");
			spec.partitionEquals().put("gsi1pk2", "shard-3");
			spec.sortConditions().add(new DynamoDbQuerySpec.SortCondition("gsi1sk",
					DynamoDbQuerySpec.SortCondition.Op.BEGINS_WITH, "MATCH#", null));
			spec.sortConditionIsTemplateColumn(true);

			DynamoDbQueryRequest request = DynamoDbQuerySpecMapper.toTemplateSortKeyRequest(spec);

			assertAll(
					() -> assertEquals("#tk0 = :tk0 AND #tk1 = :tk1 AND begins_with(#tk2, :tk2)",
							request.getKeyConditionExpression()),
					() -> assertEquals("gsi1pk", request.getExpressionAttributeNames().get("#tk0")),
					() -> assertEquals("gsi1pk2", request.getExpressionAttributeNames().get("#tk1")),
					() -> assertEquals("gsi1sk", request.getExpressionAttributeNames().get("#tk2")));
		}

		@Test
		@DisplayName("consistentRead reaches the IndexQueryBuilder path")
		void consistentReadReachesTheIndexQueryBuilderPath() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			DynamoDbQuerySpec spec = DynamoDbQuerySpec.forIndex("");
			spec.partitionEquals().put("pk", PK_P1);
			spec.consistentRead(true);

			DynamoDbQueryRequest request = DynamoDbQuerySpecMapper
					.applyTo(operations.query(Match.class, ""), spec, null, null).build();

			assertEquals(Boolean.TRUE, request.getConsistentRead(),
					"consistentRead must not be dropped when key conditions are assembled by the builder");
		}
	}

	// ──────────────────────────────────────────────────────────────────────────────
	// Helpers
	// ──────────────────────────────────────────────────────────────────────────────

	private static final String PK_TOURNAMENT = "TOURNAMENT#winter2026";

	@Nullable
	private Object executeUpdate(String methodName, Match updated) throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		operations.scriptedUpdatedEntity = updated;
		StringBasedDynamoDbQuery query = stringQuery(operations, MatchRepository.class, methodName, String.class,
				String.class, String.class);
		return query.execute(new Object[] { PK_P1, SK_M1, WINNER });
	}
}
