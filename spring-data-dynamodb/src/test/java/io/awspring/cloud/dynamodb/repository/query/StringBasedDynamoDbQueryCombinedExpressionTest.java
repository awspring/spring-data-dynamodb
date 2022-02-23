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
package io.awspring.cloud.dynamodb.repository.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.AllowScan;
import io.awspring.cloud.dynamodb.repository.ExpressionName;
import io.awspring.cloud.dynamodb.repository.ExpressionValue;
import io.awspring.cloud.dynamodb.repository.Modifying;
import io.awspring.cloud.dynamodb.repository.Query;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ValueExpressionDelegate;

public class StringBasedDynamoDbQueryCombinedExpressionTest {

	@Table(tableName = "tournament_arena")
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

		@Query(keyConditionExpression = "#pk = :pk AND #sk BETWEEN :from AND :to", filterExpression = "#region = :region", indexName = "GSI1", limit = 25, names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#sk", value = "sk"),
				@ExpressionName(name = "#region", value = "region") })
		List<Match> findInRangeInRegion(@Param("pk") String pk, @Param("from") String from, @Param("to") String to,
				@Param("region") String region);

		@Query(keyConditionExpression = "#pk = :pk", filterExpression = "#region = :region", indexName = "GSI1", limit = 10, conditionExpression = "attribute_exists(#pk)", names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#region", value = "region") })
		List<Match> findWithStrayConditionExpression(@Param("pk") String pk, @Param("region") String region);


		@Query(keyConditionExpression = "#pk = :pk", filterExpression = "#round = :round", indexName = "GSI1", limit = 5, consistentRead = true, names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#round", value = "round") })
		List<Match> findConsistentlyLimited(@Param("pk") String pk, @Param("round") String round);

		@Query(keyConditionExpression = "#pk = :pk", filterExpression = "#region = :region", indexName = "GSI1", names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#region", value = "region") })
		List<Match> findUnlimited(@Param("pk") String pk, @Param("region") String region);

		@Query(keyConditionExpression = "#pk = :pk", filterExpression = "#region = :region", indexName = "GSI1", limit = 7, names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#region", value = "region") })
		List<Match> findWithCompetingLimits(@Param("pk") String pk, @Param("region") String region, Limit limit);

		@AllowScan
		@Query(filterExpression = "#region = :region", limit = 3, names = @ExpressionName(name = "#region", value = "region"))
		List<Match> scanByRegionLimited(@Param("region") String region);

		@Query(keyConditionExpression = "#pk = :pk", filterExpression = "#undeclared = :region", indexName = "GSI1", limit = 4, names = @ExpressionName(name = "#pk", value = "pk"))
		List<Match> findWithUndeclaredFilterAlias(@Param("pk") String pk, @Param("region") String region);

		@AllowScan
		@Query(filterExpression = "#undeclared = :region", limit = 4)
		List<Match> scanWithUndeclaredFilterAlias(@Param("region") String region);

		@Modifying
		@Query(updateExpression = "SET #winner = :winner", conditionExpression = "attribute_exists(#pk) AND #round = :round", names = {
				@ExpressionName(name = "#winner", value = "winner"), @ExpressionName(name = "#pk", value = "pk"),
				@ExpressionName(name = "#round", value = "round") })
		Match recordWinner(@Param("pk") String pk, @Param("sk") String sk, @Param("winner") String winner,
				@Param("round") String round);

		@Modifying
		@Query(updateExpression = "SET #winner = :winner", conditionExpression = "#round = :expectedRound", names = {
				@ExpressionName(name = "#winner", value = "winner"),
				@ExpressionName(name = "#round", value = "round") }, values = @ExpressionValue(name = ":expectedRound", value = "FINAL"))
		Match recordWinnerInFinal(@Param("pk") String pk, @Param("sk") String sk, @Param("winner") String winner);

		@Modifying
		@Query(updateExpression = "SET #winner = :winner", conditionExpression = "#round = :expectedRound", names = {
				@ExpressionName(name = "#winner", value = "winner"),
				@ExpressionName(name = "#round", value = "round") }, values = @ExpressionValue(name = ":expectedRound", value = "#{'FIN' + 'AL'}"))
		Match recordWinnerInFinalViaSpel(@Param("pk") String pk, @Param("sk") String sk,
				@Param("winner") String winner);

		@Modifying
		@Query(updateExpression = "SET #winner = :winner", names = @ExpressionName(name = "#winner", value = "winner"))
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


	@Test
	void keyConditionAndFilterAndLimitAllLandOnTheSameQueryRequest() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "findInRangeInRegion", String.class, String.class,
				String.class, String.class);

		query.execute(new Object[] { "TOURNAMENT#winter2026", "MATCH#2026-02-01", "MATCH#2026-02-28", "EU" });

		assertNotNull(operations.lastCapturedRequest, "a keyConditionExpression must produce a Query, not a Scan");
		assertNull(operations.lastCapturedScanRequest, "a keyConditionExpression must never fall back to a Scan");

		assertEquals("GSI1", operations.lastCapturedRequest.getIndexName());
		assertEquals("#pk = :pk AND #sk BETWEEN :from AND :to",
				operations.lastCapturedRequest.getKeyConditionExpression());
		assertEquals("#region = :region", operations.lastCapturedRequest.getFilterExpression(),
				"the filterExpression must survive alongside the key condition");
		assertEquals(25, operations.lastCapturedPageRequest.getLimit(),
				"@Query(limit=...) must reach the page request");
	}

	@Test
	void valuesFromTheKeyConditionAndTheFilterAreMergedIntoOneValueMap() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "findInRangeInRegion", String.class, String.class,
				String.class, String.class);

		query.execute(new Object[] { "TOURNAMENT#winter2026", "MATCH#2026-02-01", "MATCH#2026-02-28", "EU" });

		var values = operations.lastCapturedRequest.getExpressionAttributeValues();
		assertEquals(4, values.size(), "three key-condition values plus one filter value");
		assertEquals("TOURNAMENT#winter2026", values.get(":pk"));
		assertEquals("MATCH#2026-02-01", values.get(":from"));
		assertEquals("MATCH#2026-02-28", values.get(":to"));
		assertEquals("EU", values.get(":region"), "the filter's value must be merged in, not dropped");
	}

	@Test
	void namesFromTheKeyConditionAndTheFilterAreMergedIntoOneNameMap() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "findInRangeInRegion", String.class, String.class,
				String.class, String.class);

		query.execute(new Object[] { "TOURNAMENT#winter2026", "MATCH#2026-02-01", "MATCH#2026-02-28", "EU" });

		var names = operations.lastCapturedRequest.getExpressionAttributeNames();
		assertEquals("pk", names.get("#pk"));
		assertEquals("sk", names.get("#sk"));
		assertEquals("region", names.get("#region"), "the filter's alias must be present too");
	}

	@Test
	void consistentReadAppliesWhenCombinedWithAFilterAndALimit() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "findConsistentlyLimited", String.class, String.class);

		query.execute(new Object[] { "TOURNAMENT#winter2026", "FINAL" });

		assertEquals(Boolean.TRUE, operations.lastCapturedRequest.getConsistentRead());
		assertEquals("#round = :round", operations.lastCapturedRequest.getFilterExpression());
		assertEquals(5, operations.lastCapturedPageRequest.getLimit());
	}

	@Test
	void omittingLimitLeavesTheRequestUnlimited() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "findUnlimited", String.class, String.class);

		query.execute(new Object[] { "TOURNAMENT#winter2026", "EU" });

		assertEquals("#region = :region", operations.lastCapturedRequest.getFilterExpression());
		assertNull(operations.lastCapturedPageRequest.getLimit(),
				"limit() defaults to -1, which must not become an explicit limit");
	}

	@Test
	void anAnnotationLimitWinsOverALimitArgument() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "findWithCompetingLimits", String.class, String.class,
				Limit.class);

		query.execute(new Object[] { "TOURNAMENT#winter2026", "EU", Limit.of(99) });

		assertEquals(7, operations.lastCapturedPageRequest.getLimit(),
				"an explicit @Query(limit=...) takes precedence over a Limit parameter");
	}

	@Test
	void filterAndLimitWithoutAKeyConditionTakeTheScanPath() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "scanByRegionLimited", String.class);

		query.execute(new Object[] { "EU" });

		assertNotNull(operations.lastCapturedScanRequest, "no keyConditionExpression means a Scan");
		assertNull(operations.lastCapturedRequest);
		assertEquals("#region = :region", operations.lastCapturedScanRequest.getFilterExpression());
		assertEquals(3, operations.lastCapturedScanRequest.getLimit(), "the limit must reach the scan request");
	}


	@Test
	void conditionExpressionIsIgnoredOnAReadQuery() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "findWithStrayConditionExpression", String.class,
				String.class);

		query.execute(new Object[] { "TOURNAMENT#winter2026", "EU" });

		assertEquals("#pk = :pk", operations.lastCapturedRequest.getKeyConditionExpression());
		assertEquals("#region = :region", operations.lastCapturedRequest.getFilterExpression());
		assertEquals(10, operations.lastCapturedPageRequest.getLimit());

		assertFalse(operations.lastCapturedRequest.getFilterExpression().contains("attribute_exists"),
				"conditionExpression must not be folded into the filterExpression");
		assertFalse(operations.lastCapturedRequest.getKeyConditionExpression().contains("attribute_exists"),
				"conditionExpression must not be folded into the keyConditionExpression");
	}

	@Test
	void anUndeclaredFilterAliasIsLeftUnresolvedOnTheQueryPath() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "findWithUndeclaredFilterAlias", String.class,
				String.class);

		query.execute(new Object[] { "TOURNAMENT#winter2026", "EU" });

		var names = operations.lastCapturedRequest.getExpressionAttributeNames();
		assertEquals("pk", names.get("#pk"));
		assertNull(names.get("#undeclared"),
				"the raw key-condition path does not auto-derive aliases: declare them in names()");
	}

	@Test
	void anUndeclaredFilterAliasIsAutoResolvedOnTheScanPath() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "scanWithUndeclaredFilterAlias", String.class);

		query.execute(new Object[] { "EU" });

		assertEquals("undeclared", operations.lastCapturedScanRequest.getExpressionAttributeNames().get("#undeclared"),
				"the scan path derives an undeclared alias from the placeholder itself");
	}

	@Test
	void updateExpressionAndConditionExpressionBothLandOnTheUpdateRequest() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "recordWinner", String.class, String.class, String.class,
				String.class);

		DynamoDbParametersParameterAccessor accessor = new DynamoDbParametersParameterAccessor(query.getQueryMethod(),
				"TOURNAMENT#winter2026", "MATCH#m1", "player-7", "FINAL");
		AbstractDynamoDbQuery.ModifyingUpdate update = query.createUpdateRequest(accessor);

		assertEquals("TOURNAMENT#winter2026", update.partitionKey(), "the partition key comes from @Param(\"pk\")");
		assertEquals("MATCH#m1", update.sortKey(), "the sort key comes from @Param(\"sk\")");
		assertEquals("SET #winner = :winner", update.request().getUpdateExpression());
		assertEquals("attribute_exists(#pk) AND #round = :round", update.request().getConditionExpression());
	}

	@Test
	void valuesFromTheUpdateAndTheConditionAreMergedOnTheUpdateRequest() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "recordWinner", String.class, String.class, String.class,
				String.class);

		DynamoDbParametersParameterAccessor accessor = new DynamoDbParametersParameterAccessor(query.getQueryMethod(),
				"TOURNAMENT#winter2026", "MATCH#m1", "player-7", "FINAL");
		AbstractDynamoDbQuery.ModifyingUpdate update = query.createUpdateRequest(accessor);

		var values = update.request().getExpressionAttributeValues();
		assertEquals("player-7", values.get(":winner"), "the update's value");
		assertEquals("FINAL", values.get(":round"), "the condition's value must be merged in as well");

		var names = update.request().getExpressionAttributeNames();
		assertEquals("winner", names.get("#winner"));
		assertEquals("pk", names.get("#pk"));
		assertEquals("round", names.get("#round"));
	}

	@Test
	void anExpressionValueConstantIsAvailableToTheConditionExpression() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "recordWinnerInFinal", String.class, String.class,
				String.class);

		DynamoDbParametersParameterAccessor accessor = new DynamoDbParametersParameterAccessor(query.getQueryMethod(),
				"TOURNAMENT#winter2026", "MATCH#m1", "player-7");
		AbstractDynamoDbQuery.ModifyingUpdate update = query.createUpdateRequest(accessor);

		assertEquals("#round = :expectedRound", update.request().getConditionExpression());
		assertEquals("FINAL", update.request().getExpressionAttributeValues().get(":expectedRound"),
				"a plain @ExpressionValue is taken as a literal, so no quoting is needed");
		assertEquals("player-7", update.request().getExpressionAttributeValues().get(":winner"));
	}

	@Test
	void anExpressionValueWrittenAsSpelIsEvaluatedForTheConditionExpression() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "recordWinnerInFinalViaSpel", String.class, String.class,
				String.class);

		DynamoDbParametersParameterAccessor accessor = new DynamoDbParametersParameterAccessor(query.getQueryMethod(),
				"TOURNAMENT#winter2026", "MATCH#m1", "player-7");
		AbstractDynamoDbQuery.ModifyingUpdate update = query.createUpdateRequest(accessor);

		assertEquals("FINAL", update.request().getExpressionAttributeValues().get(":expectedRound"),
				"a #{...} @ExpressionValue is evaluated as SpEL rather than passed through");
	}

	@Test
	void omittingConditionExpressionLeavesItNullRatherThanEmpty() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "recordWinnerUnconditionally", String.class, String.class,
				String.class);

		DynamoDbParametersParameterAccessor accessor = new DynamoDbParametersParameterAccessor(query.getQueryMethod(),
				"TOURNAMENT#winter2026", "MATCH#m1", "player-7");
		AbstractDynamoDbQuery.ModifyingUpdate update = query.createUpdateRequest(accessor);

		assertEquals("SET #winner = :winner", update.request().getUpdateExpression());
		assertNull(update.request().getConditionExpression(),
				"an unset conditionExpression must stay null so no ConditionExpression is sent");
	}
}
