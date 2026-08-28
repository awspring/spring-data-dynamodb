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

import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.repository.AllowScan;
import io.awspring.spring.data.dynamodb.repository.ExpressionName;
import io.awspring.spring.data.dynamodb.repository.ExpressionValue;
import io.awspring.spring.data.dynamodb.repository.Query;
import io.awspring.spring.data.dynamodb.repository.Update;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Limit;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ValueExpressionDelegate;

@DisplayName("StringBasedDynamoDbQuery combined expressions")
class StringBasedDynamoDbQueryCombinedExpressionTest {

	private static final String TABLE_NAME = "tournament_arena";
	private static final String INDEX_GSI1 = "GSI1";
	private static final String PK_TOURNAMENT = "TOURNAMENT#winter2026";
	private static final String SK_FROM = "MATCH#2026-02-01";
	private static final String SK_TO = "MATCH#2026-02-28";
	private static final String REGION_EU = "EU";
	private static final String ROUND_FINAL = "FINAL";

	@Table(tableName = TABLE_NAME)
	static class Match {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String region;
		String round;
		String winner;
	}

	interface MatchRepository extends Repository<Match, String> {

		@Query(keyConditionExpression = "#pk = :pk AND #sk BETWEEN :from AND :to", filterExpression = "#region = :region", indexName = INDEX_GSI1, limit = 25, names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#sk", value = "sk"),
				@ExpressionName(name = "#region", value = "region") })
		List<Match> findInRangeInRegion(@Param("pk") String pk, @Param("from") String from, @Param("to") String to,
				@Param("region") String region);

		@Query(keyConditionExpression = "#pk = :pk", filterExpression = "#round = :round", indexName = INDEX_GSI1, limit = 5, consistentRead = true, names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#round", value = "round") })
		List<Match> findConsistentlyLimited(@Param("pk") String pk, @Param("round") String round);

		@Query(keyConditionExpression = "#pk = :pk", filterExpression = "#region = :region", indexName = INDEX_GSI1, names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#region", value = "region") })
		List<Match> findUnlimited(@Param("pk") String pk, @Param("region") String region);

		@Query(keyConditionExpression = "#pk = :pk", filterExpression = "#region = :region", indexName = INDEX_GSI1, limit = 7, names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#region", value = "region") })
		List<Match> findWithCompetingLimits(@Param("pk") String pk, @Param("region") String region, Limit limit);

		@AllowScan
		@Query(filterExpression = "#region = :region", limit = 3, names = @ExpressionName(name = "#region", value = "region"))
		List<Match> scanByRegionLimited(@Param("region") String region);

		@Query(keyConditionExpression = "#pk = :pk", filterExpression = "#undeclared = :region", indexName = INDEX_GSI1, limit = 4, names = @ExpressionName(name = "#pk", value = "pk"))
		List<Match> findWithUndeclaredFilterAlias(@Param("pk") String pk, @Param("region") String region);

		@AllowScan
		@Query(filterExpression = "#undeclared = :region", limit = 4)
		List<Match> scanWithUndeclaredFilterAlias(@Param("region") String region);

		@Update(updateExpression = "SET #winner = :winner", conditionExpression = "attribute_exists(#pk) AND #round = :round", names = {
				@ExpressionName(name = "#winner", value = "winner"), @ExpressionName(name = "#pk", value = "pk"),
				@ExpressionName(name = "#round", value = "round") })
		Match recordWinner(@Param("pk") String pk, @Param("sk") String sk, @Param("winner") String winner,
				@Param("round") String round);

		@Update(updateExpression = "SET #winner = :winner", conditionExpression = "#round = :expectedRound", names = {
				@ExpressionName(name = "#winner", value = "winner"),
				@ExpressionName(name = "#round", value = "round") }, values = @ExpressionValue(name = ":expectedRound", value = "FINAL"))
		Match recordWinnerInFinal(@Param("pk") String pk, @Param("sk") String sk, @Param("winner") String winner);

		@Update(updateExpression = "SET #winner = :winner", conditionExpression = "#round = :expectedRound", names = {
				@ExpressionName(name = "#winner", value = "winner"),
				@ExpressionName(name = "#round", value = "round") }, values = @ExpressionValue(name = ":expectedRound", value = "#{'FIN' + 'AL'}"))
		Match recordWinnerInFinalViaSpel(@Param("pk") String pk, @Param("sk") String sk,
				@Param("winner") String winner);

		@Update(updateExpression = "SET #winner = :winner", names = @ExpressionName(name = "#winner", value = "winner"))
		Match recordWinnerUnconditionally(@Param("pk") String pk, @Param("sk") String sk,
				@Param("winner") String winner);
	}

	private PartTreeDynamoDbQueryReplayTest.CapturingOperations operations() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		return new PartTreeDynamoDbQueryReplayTest.CapturingOperations(converter);
	}

	private StringBasedDynamoDbQuery queryFor(PartTreeDynamoDbQueryReplayTest.CapturingOperations operations,
			String name, Class<?>... paramTypes) throws NoSuchMethodException {
		Method method = MatchRepository.class.getMethod(name, paramTypes);
		RepositoryMetadata metadata = new DefaultRepositoryMetadata(MatchRepository.class);
		ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		DynamoDbQueryMethod queryMethod = new DynamoDbQueryMethod(method, metadata, projectionFactory, mappingContext);
		return new StringBasedDynamoDbQuery(queryMethod, operations, ValueExpressionDelegate.create());
	}

	@Nested
	@DisplayName("Key condition + filter + limit combined")
	class CombinedQueryTests {

		@Test
		@DisplayName("key condition, filter, and limit all land on the same query request")
		void keyConditionAndFilterAndLimitAllLandOnTheSameQueryRequest() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "findInRangeInRegion", String.class, String.class,
					String.class, String.class);

			// Act
			query.execute(new Object[] { PK_TOURNAMENT, SK_FROM, SK_TO, REGION_EU });

			// Assert
			assertAll(
					() -> assertNotNull(operations.lastCapturedRequest,
							"a keyConditionExpression must produce a Query, not a Scan"),
					() -> assertNull(operations.lastCapturedScanRequest,
							"a keyConditionExpression must never fall back to a Scan"),
					() -> assertEquals(INDEX_GSI1, operations.lastCapturedRequest.getIndexName()),
					() -> assertEquals("#pk = :pk AND #sk BETWEEN :from AND :to",
							operations.lastCapturedRequest.getKeyConditionExpression()),
					() -> assertEquals("#region = :region", operations.lastCapturedRequest.getFilterExpression(),
							"the filterExpression must survive alongside the key condition"),
					() -> assertEquals(25, operations.lastCapturedPageRequest.getLimit(),
							"@Query(limit=...) must reach the page request"));
		}

		@Test
		@DisplayName("values from key condition and filter are merged into one value map")
		void valuesFromTheKeyConditionAndTheFilterAreMergedIntoOneValueMap() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "findInRangeInRegion", String.class, String.class,
					String.class, String.class);

			// Act
			query.execute(new Object[] { PK_TOURNAMENT, SK_FROM, SK_TO, REGION_EU });

			// Assert
			var values = operations.lastCapturedRequest.getExpressionAttributeValues();
			assertAll(() -> assertEquals(4, values.size(), "three key-condition values plus one filter value"),
					() -> assertEquals(PK_TOURNAMENT, values.get(":pk")),
					() -> assertEquals(SK_FROM, values.get(":from")), () -> assertEquals(SK_TO, values.get(":to")),
					() -> assertEquals(REGION_EU, values.get(":region"),
							"the filter's value must be merged in, not dropped"));
		}

		@Test
		@DisplayName("names from key condition and filter are merged into one name map")
		void namesFromTheKeyConditionAndTheFilterAreMergedIntoOneNameMap() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "findInRangeInRegion", String.class, String.class,
					String.class, String.class);

			// Act
			query.execute(new Object[] { PK_TOURNAMENT, SK_FROM, SK_TO, REGION_EU });

			// Assert
			var names = operations.lastCapturedRequest.getExpressionAttributeNames();
			assertAll(() -> assertEquals("pk", names.get("#pk")), () -> assertEquals("sk", names.get("#sk")),
					() -> assertEquals("region", names.get("#region"), "the filter's alias must be present too"));
		}
	}

	@Nested
	@DisplayName("Limit handling")
	class LimitTests {

		@Test
		@DisplayName("consistentRead on a GSI is rejected before filter and limit execution")
		void consistentReadOnAGsiIsRejectedBeforeFilterAndLimitExecution() {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();

			assertThrows(InvalidDataAccessApiUsageException.class,
					() -> queryFor(operations, "findConsistentlyLimited", String.class, String.class));
		}

		@Test
		@DisplayName("omitting limit leaves the request unlimited")
		void omittingLimitLeavesTheRequestUnlimited() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "findUnlimited", String.class, String.class);

			query.execute(new Object[] { PK_TOURNAMENT, REGION_EU });

			assertAll(() -> assertEquals("#region = :region", operations.lastCapturedRequest.getFilterExpression()),
					() -> assertNull(operations.lastCapturedPageRequest.getLimit(),
							"limit() defaults to -1, which must not become an explicit limit"));
		}

		@Test
		@DisplayName("an annotation limit wins over a Limit argument")
		void anAnnotationLimitWinsOverALimitArgument() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "findWithCompetingLimits", String.class, String.class,
					Limit.class);

			query.execute(new Object[] { PK_TOURNAMENT, REGION_EU, Limit.of(99) });

			assertEquals(7, operations.lastCapturedPageRequest.getLimit(),
					"an explicit @Query(limit=...) takes precedence over a Limit parameter");
		}
	}

	@Nested
	@DisplayName("Scan path")
	class ScanPathTests {

		@Test
		@DisplayName("filter and limit without a key condition take the scan path")
		void filterAndLimitWithoutAKeyConditionTakeTheScanPath() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "scanByRegionLimited", String.class);

			query.execute(new Object[] { REGION_EU });

			assertAll(() -> assertNotNull(operations.lastCapturedScanRequest, "no keyConditionExpression means a Scan"),
					() -> assertNull(operations.lastCapturedRequest),
					() -> assertEquals("#region = :region", operations.lastCapturedScanRequest.getFilterExpression()),
					() -> assertEquals(3, operations.lastCapturedScanRequest.getLimit(),
							"the limit must reach the scan request"));
		}
	}

	@Nested
	@DisplayName("Undeclared alias auto-resolution")
	class UndeclaredAliasTests {

		@Test
		@DisplayName("auto-resolved on the query path")
		void anUndeclaredFilterAliasIsAutoResolvedOnTheQueryPathToo() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "findWithUndeclaredFilterAlias", String.class,
					String.class);

			query.execute(new Object[] { PK_TOURNAMENT, REGION_EU });

			var names = operations.lastCapturedRequest.getExpressionAttributeNames();
			assertAll(() -> assertEquals("pk", names.get("#pk"), "a declared alias still wins"),
					() -> assertEquals("undeclared", names.get("#undeclared"),
							"an undeclared alias is derived from the placeholder itself"));
		}

		@Test
		@DisplayName("auto-resolved on the scan path")
		void anUndeclaredFilterAliasIsAutoResolvedOnTheScanPath() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "scanWithUndeclaredFilterAlias", String.class);

			query.execute(new Object[] { REGION_EU });

			assertEquals("undeclared",
					operations.lastCapturedScanRequest.getExpressionAttributeNames().get("#undeclared"),
					"the scan path derives an undeclared alias from the placeholder itself");
		}
	}

	@Nested
	@DisplayName("@Update update expression handling")
	class UpdateExecutionTests {

		@Test
		@DisplayName("updateExpression and conditionExpression both land on the update request")
		void updateExpressionAndConditionExpressionBothLandOnTheUpdateRequest() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "recordWinner", String.class, String.class,
					String.class, String.class);

			DynamoDbParametersParameterAccessor accessor = new DynamoDbParametersParameterAccessor(
					query.getQueryMethod(), PK_TOURNAMENT, "MATCH#m1", "player-7", ROUND_FINAL);
			AbstractDynamoDbQuery.UpdateExecution update = query.createUpdateRequest(accessor);

			assertAll(
					() -> assertEquals(PK_TOURNAMENT, update.partitionKey(),
							"the partition key comes from @Param(\"pk\")"),
					() -> assertEquals("MATCH#m1", update.sortKey(), "the sort key comes from @Param(\"sk\")"),
					() -> assertEquals("SET #winner = :winner", update.request().getUpdateExpression()),
					() -> assertEquals("attribute_exists(#pk) AND #round = :round",
							update.request().getConditionExpression()));
		}

		@Test
		@DisplayName("values from the update and the condition are merged")
		void valuesFromTheUpdateAndTheConditionAreMergedOnTheUpdateRequest() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "recordWinner", String.class, String.class,
					String.class, String.class);

			DynamoDbParametersParameterAccessor accessor = new DynamoDbParametersParameterAccessor(
					query.getQueryMethod(), PK_TOURNAMENT, "MATCH#m1", "player-7", ROUND_FINAL);
			AbstractDynamoDbQuery.UpdateExecution update = query.createUpdateRequest(accessor);

			var values = update.request().getExpressionAttributeValues();
			var names = update.request().getExpressionAttributeNames();
			assertAll(() -> assertEquals("player-7", values.get(":winner"), "the update's value"),
					() -> assertEquals(ROUND_FINAL, values.get(":round"),
							"the condition's value must be merged in as well"),
					() -> assertEquals("winner", names.get("#winner")), () -> assertEquals("pk", names.get("#pk")),
					() -> assertEquals("round", names.get("#round")));
		}

		@Test
		@DisplayName("an @ExpressionValue constant is available to the conditionExpression")
		void anExpressionValueConstantIsAvailableToTheConditionExpression() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "recordWinnerInFinal", String.class, String.class,
					String.class);

			DynamoDbParametersParameterAccessor accessor = new DynamoDbParametersParameterAccessor(
					query.getQueryMethod(), PK_TOURNAMENT, "MATCH#m1", "player-7");
			AbstractDynamoDbQuery.UpdateExecution update = query.createUpdateRequest(accessor);

			assertAll(() -> assertEquals("#round = :expectedRound", update.request().getConditionExpression()),
					() -> assertEquals("FINAL", update.request().getExpressionAttributeValues().get(":expectedRound"),
							"a plain @ExpressionValue is taken as a literal"),
					() -> assertEquals("player-7", update.request().getExpressionAttributeValues().get(":winner")));
		}

		@Test
		@DisplayName("an @ExpressionValue written as SpEL is evaluated")
		void anExpressionValueWrittenAsSpelIsEvaluatedForTheConditionExpression() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "recordWinnerInFinalViaSpel", String.class,
					String.class, String.class);

			DynamoDbParametersParameterAccessor accessor = new DynamoDbParametersParameterAccessor(
					query.getQueryMethod(), PK_TOURNAMENT, "MATCH#m1", "player-7");
			AbstractDynamoDbQuery.UpdateExecution update = query.createUpdateRequest(accessor);

			assertEquals("FINAL", update.request().getExpressionAttributeValues().get(":expectedRound"),
					"a #{...} @ExpressionValue is evaluated as SpEL rather than passed through");
		}

		@Test
		@DisplayName("omitting conditionExpression leaves it null rather than empty")
		void omittingConditionExpressionLeavesItNullRatherThanEmpty() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "recordWinnerUnconditionally", String.class,
					String.class, String.class);

			DynamoDbParametersParameterAccessor accessor = new DynamoDbParametersParameterAccessor(
					query.getQueryMethod(), PK_TOURNAMENT, "MATCH#m1", "player-7");
			AbstractDynamoDbQuery.UpdateExecution update = query.createUpdateRequest(accessor);

			assertAll(() -> assertEquals("SET #winner = :winner", update.request().getUpdateExpression()),
					() -> assertNull(update.request().getConditionExpression(),
							"an unset conditionExpression must stay null so no ConditionExpression is sent"));
		}
	}
}
