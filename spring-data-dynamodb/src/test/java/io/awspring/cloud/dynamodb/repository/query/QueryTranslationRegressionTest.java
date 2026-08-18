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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.EntityQueryResult;
import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.Column;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SecondaryIndex;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.AllowScan;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Window;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;

@DisplayName("Query translation regression tests")
class QueryTranslationRegressionTest {

	private static final String TABLE_NAME = "arena";
	private static final String PK_P1 = "P1";

	@Table(tableName = TABLE_NAME)
	static class Row {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		Integer score;
		String region;
	}

	interface RowRepository extends Repository<Row, String> {

		List<Row> findByPkAndScoreBetween(String pk, Integer lo, Integer hi);

		@AllowScan
		List<Row> findByRegionIn(List<String> regions);

		@AllowScan
		List<Row> findByRegionNotIn(List<String> regions);

		long countByPk(String pk);

		boolean existsByPk(String pk);

		long findScoreByPk(String pk);

		List<Row> findByPkAndSkBetween(String pk, String from, String to);

		List<Row> findByPkAndSkStartingWith(String pk, String prefix);

		List<Row> findByPkAndSkGreaterThan(String pk, String from);

		List<Row> findByPkAndSkLessThanEqual(String pk, String to);

		List<Row> findByPkAndSkBetweenAndRegion(String pk, String from, String to, String region);

		@AllowScan
		List<Row> findByScoreBetween(Integer lo, Integer hi);
	}

	private static DynamoDbMappingContext context() {
		DynamoDbMappingContext ctx = new DynamoDbMappingContext();
		ctx.getRequiredPersistentEntity(Row.class);
		return ctx;
	}

	private static PartTreeDynamoDbQueryReplayTest.CapturingOperations operations() {
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(context());
		converter.afterPropertiesSet();
		return new PartTreeDynamoDbQueryReplayTest.CapturingOperations(converter);
	}

	private static DynamoDbQueryMethod queryMethod(String name, Class<?>... types) throws NoSuchMethodException {
		Method method = RowRepository.class.getMethod(name, types);
		RepositoryMetadata metadata = new DefaultRepositoryMetadata(RowRepository.class);
		ProjectionFactory pf = new SpelAwareProxyProjectionFactory();
		return new DynamoDbQueryMethod(method, metadata, pf, context());
	}

	private static PartTreeDynamoDbQuery queryFor(PartTreeDynamoDbQueryReplayTest.CapturingOperations ops, String name,
			Class<?>... types) throws NoSuchMethodException {
		return new PartTreeDynamoDbQuery(queryMethod(name, types), ops);
	}

	private static DynamoDbQuerySpec specFor(String name, Object[] args, Class<?>... types)
			throws NoSuchMethodException {
		PartTreeDynamoDbQuery query = queryFor(operations(), name, types);
		return query.createQuerySpec(new DynamoDbParametersParameterAccessor(query.getQueryMethod(), args));
	}

	@Nested
	@DisplayName("BETWEEN filter binding")
	class BetweenFilterTests {

		@Test
		@DisplayName("binds both bounds to distinct placeholders")
		void betweenFilterBindsBothBoundsToDistinctPlaceholders() throws NoSuchMethodException {
			DynamoDbQuerySpec spec = specFor("findByPkAndScoreBetween", new Object[] { PK_P1, 10, 20 }, String.class,
					Integer.class, Integer.class);

			String filter = spec.filterExpression();
			assertNotNull(filter);
			Matcher matcher = Pattern.compile("BETWEEN\\s+(:\\w+)\\s+AND\\s+(:\\w+)$").matcher(filter);
			assertTrue(matcher.find(), "expected a BETWEEN fragment, got: " + filter);
			assertAll(
					() -> assertFalse(matcher.group(1).equals(matcher.group(2)),
							"the two bounds must not share one placeholder"),
					() -> assertEquals(2, spec.expressionAttributeValues().size(), "both bounds must be bound"),
					() -> assertEquals(10, spec.expressionAttributeValues().get(matcher.group(1)),
							"lower bound binds to the first slot"),
					() -> assertEquals(20, spec.expressionAttributeValues().get(matcher.group(2)),
							"upper bound binds to the second slot"));
		}
	}

	@Nested
	@DisplayName("IN list edge cases")
	class InListTests {

		@Test
		@DisplayName("empty IN list does not produce invalid IN() syntax")
		void emptyInListDoesNotProduceInvalidInSyntax() throws NoSuchMethodException {
			DynamoDbQuerySpec spec = specFor("findByRegionIn", new Object[] { List.of() }, List.class);

			String filter = spec.filterExpression();
			assertAll(() -> assertNotNull(filter),
					() -> assertFalse(filter.contains("IN ()"), "'IN ()' is a DynamoDB syntax error"),
					() -> assertTrue(spec.expressionAttributeValues().isEmpty(),
							"an empty operand list binds no values"));
		}

		@Test
		@DisplayName("empty NOT IN list is satisfied by every item")
		void emptyNotInListIsSatisfiedByEveryItem() throws NoSuchMethodException {
			DynamoDbQuerySpec spec = specFor("findByRegionNotIn", new Object[] { List.of() }, List.class);

			String filter = spec.filterExpression();
			assertAll(() -> assertNotNull(filter), () -> assertFalse(filter.contains("IN ()")),
					() -> assertTrue(filter.startsWith("attribute_not_exists("),
							"nothing is a member of the empty set, so NotIn matches everything"));
		}

		@Test
		@DisplayName("non-empty IN list expands to one placeholder per element")
		void nonEmptyInListStillExpandsToOnePlaceholderPerElement() throws NoSuchMethodException {
			DynamoDbQuerySpec spec = specFor("findByRegionIn", new Object[] { List.of("EU", "US", "APAC") },
					List.class);

			assertAll(() -> assertEquals(3, spec.expressionAttributeValues().size()),
					() -> assertTrue(spec.filterExpression().contains(" IN (")),
					() -> assertTrue(spec.expressionAttributeValues().containsValue("EU")),
					() -> assertTrue(spec.expressionAttributeValues().containsValue("APAC")));
		}
	}

	@Nested
	@DisplayName("Count and exists classification")
	class CountAndExistsTests {

		@Test
		@DisplayName("derived count and exists are classified from the method name")
		void derivedCountAndExistsAreClassifiedFromTheMethodName() throws NoSuchMethodException {
			assertAll(
					() -> assertTrue(queryMethod("countByPk", String.class).isCountQuery(),
							"countBy... is a count query"),
					() -> assertTrue(queryMethod("existsByPk", String.class).isExistsQuery(),
							"existsBy... is an exists query"));
		}

		@Test
		@DisplayName("a long-returning finder is not treated as a count query")
		void aLongReturningFinderIsNotTreatedAsACountQuery() throws NoSuchMethodException {
			DynamoDbQueryMethod method = queryMethod("findScoreByPk", String.class);
			assertFalse(method.isCountQuery(), "findScoreByPk returns a long but is a finder, not a count");
		}

		@Test
		@DisplayName("count uses a Query and honours the partition key")
		void countUsesAQueryAndHonoursThePartitionKey() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations ops = operations();
			ops.scriptedCount = 2L;
			PartTreeDynamoDbQuery query = queryFor(ops, "countByPk", String.class);

			Object count = query.execute(new Object[] { PK_P1 });

			assertAll(() -> assertEquals(2L, count),
					() -> assertNotNull(ops.lastCapturedRequest,
							"count must run as a Query when the partition key is known"),
					() -> assertTrue(ops.countedViaQuery, "count must be delegated to the Select.COUNT query"),
					() -> assertNull(ops.lastCapturedScanRequest, "count must not fall back to a Scan"),
					() -> assertEquals(PK_P1,
							ops.lastCapturedRequest.getExpressionAttributeValues().values().iterator().next()));
		}

		@Test
		@DisplayName("exists uses a Query and honours the partition key")
		void existsUsesAQueryAndHonoursThePartitionKey() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations ops = operations();
			ops.scriptedCount = 1L;
			PartTreeDynamoDbQuery query = queryFor(ops, "existsByPk", String.class);

			Object exists = query.execute(new Object[] { PK_P1 });

			assertAll(() -> assertEquals(true, exists), () -> assertNotNull(ops.lastCapturedRequest),
					() -> assertTrue(ops.countedViaQuery), () -> assertNull(ops.lastCapturedScanRequest));
		}

		@Test
		@DisplayName("exists is false when the query returns nothing")
		void existsIsFalseWhenTheQueryReturnsNothing() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations ops = operations();
			ops.scriptedQueryResult = PartTreeDynamoDbQueryReplayTest.EntityQueryResultAccess.of(List.of());
			PartTreeDynamoDbQuery query = queryFor(ops, "existsByPk", String.class);

			assertEquals(false, query.execute(new Object[] { PK_P1 }));
		}

		@Test
		@DisplayName("count is zero when the query returns nothing")
		void countIsZeroWhenTheQueryReturnsNothing() throws NoSuchMethodException {
			PartTreeDynamoDbQueryReplayTest.CapturingOperations ops = operations();
			ops.scriptedQueryResult = PartTreeDynamoDbQueryReplayTest.EntityQueryResultAccess.of(List.of());
			PartTreeDynamoDbQuery query = queryFor(ops, "countByPk", String.class);

			assertEquals(0L, query.execute(new Object[] { PK_P1 }));
		}
	}

	@Nested
	@DisplayName("Sort key conditions (BETWEEN, begins_with, comparisons)")
	class SortKeyConditionTests {

		@Test
		@DisplayName("sort-key BETWEEN becomes a key condition with both bounds")
		void sortKeyBetweenBecomesAKeyConditionWithBothBounds() throws NoSuchMethodException {
			DynamoDbQuerySpec spec = specFor("findByPkAndSkBetween", new Object[] { PK_P1, "M#a", "M#z" }, String.class,
					String.class, String.class);

			DynamoDbQuerySpec.SortCondition condition = spec.sortConditions().get(0);
			assertAll(() -> assertEquals(1, spec.sortConditions().size()),
					() -> assertEquals("sk", condition.columnName()),
					() -> assertEquals(DynamoDbQuerySpec.SortCondition.Op.BETWEEN, condition.op()),
					() -> assertEquals("M#a", condition.value()), () -> assertEquals("M#z", condition.rangeEnd()),
					() -> assertNull(spec.filterExpression()));
		}

		@Test
		@DisplayName("sort-key startingWith becomes a begins_with key condition")
		void sortKeyStartingWithBecomesABeginsWithKeyCondition() throws NoSuchMethodException {
			DynamoDbQuerySpec spec = specFor("findByPkAndSkStartingWith", new Object[] { PK_P1, "MATCH#" },
					String.class, String.class);

			assertAll(() -> assertEquals(1, spec.sortConditions().size()),
					() -> assertEquals(DynamoDbQuerySpec.SortCondition.Op.BEGINS_WITH,
							spec.sortConditions().get(0).op()),
					() -> assertEquals("MATCH#", spec.sortConditions().get(0).value()),
					() -> assertNull(spec.filterExpression()));
		}

		@Test
		@DisplayName("sort-key comparisons become key conditions")
		void sortKeyComparisonsBecomeKeyConditions() throws NoSuchMethodException {
			DynamoDbQuerySpec greater = specFor("findByPkAndSkGreaterThan", new Object[] { PK_P1, "M#a" }, String.class,
					String.class);
			DynamoDbQuerySpec atMost = specFor("findByPkAndSkLessThanEqual", new Object[] { PK_P1, "M#z" },
					String.class, String.class);

			assertAll(() -> assertEquals(DynamoDbQuerySpec.SortCondition.Op.GT, greater.sortConditions().get(0).op()),
					() -> assertNull(greater.filterExpression()),
					() -> assertEquals(DynamoDbQuerySpec.SortCondition.Op.LE, atMost.sortConditions().get(0).op()),
					() -> assertNull(atMost.filterExpression()));
		}

		@Test
		@DisplayName("a non-key predicate stays a filter alongside a sort-key range")
		void aNonKeyPredicateStaysAFilterAlongsideASortKeyRange() throws NoSuchMethodException {
			DynamoDbQuerySpec spec = specFor("findByPkAndSkBetweenAndRegion",
					new Object[] { PK_P1, "M#a", "M#z", "EU" }, String.class, String.class, String.class, String.class);

			assertAll(() -> assertEquals(1, spec.sortConditions().size()),
					() -> assertEquals(DynamoDbQuerySpec.SortCondition.Op.BETWEEN, spec.sortConditions().get(0).op()),
					() -> assertNotNull(spec.filterExpression()),
					() -> assertTrue(spec.expressionAttributeValues().containsValue("EU")));
		}

		@Test
		@DisplayName("a range without a partition key degrades to a filter keeping both bounds")
		void aRangeWithoutAPartitionKeyDegradesToAFilterKeepingBothBounds() throws NoSuchMethodException {
			DynamoDbQuerySpec spec = specFor("findByScoreBetween", new Object[] { 10, 20 }, Integer.class,
					Integer.class);

			assertAll(() -> assertTrue(spec.requiresScan()), () -> assertNotNull(spec.filterExpression()),
					() -> assertTrue(spec.expressionAttributeValues().containsValue(10),
							"lower bound preserved on the scan fallback"),
					() -> assertTrue(spec.expressionAttributeValues().containsValue(20),
							"upper bound preserved on the scan fallback"));
		}
	}

	@Nested
	@DisplayName("Window pagination (positionAt semantics)")
	class WindowPositionTests {

		@Test
		@DisplayName("positionAt the last index returns the resume cursor")
		void positionAtTheLastIndexReturnsTheResumeCursor() {
			EntityQueryResult<List<Object>> result = PartTreeDynamoDbQueryReplayTest.EntityQueryResultAccess
					.of(List.<Object> of("a", "b", "c"), Map.of("pk", "LAST"));

			Window<?> window = (Window<?>) new DynamoDbQueryExecution.WindowExecution().execute(result);

			assertAll(() -> assertTrue(window.hasNext()), () -> assertNotNull(window.positionAt(window.size() - 1),
					"the last element carries the LastEvaluatedKey cursor"));
		}

		@Test
		@DisplayName("positionAt any other index is rejected rather than silently skipping rows")
		void positionAtAnyOtherIndexIsRejectedRatherThanSilentlySkippingRows() {
			EntityQueryResult<List<Object>> result = PartTreeDynamoDbQueryReplayTest.EntityQueryResultAccess
					.of(List.<Object> of("a", "b", "c"), Map.of("pk", "LAST"));

			Window<?> window = (Window<?>) new DynamoDbQueryExecution.WindowExecution().execute(result);

			IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> window.positionAt(0),
					"returning the page-end cursor for index 0 would silently skip the rest of the page");
			assertTrue(thrown.getMessage().contains("positionAt(window.size() - 1)"),
					"the message should point at the supported call, got: " + thrown.getMessage());
		}

		@Test
		@DisplayName("hasPosition reports which indices are resumable")
		void hasPositionReportsWhichIndicesAreResumable() {
			EntityQueryResult<List<Object>> result = PartTreeDynamoDbQueryReplayTest.EntityQueryResultAccess
					.of(List.<Object> of("a", "b", "c"), Map.of("pk", "LAST"));

			Window<?> window = (Window<?>) new DynamoDbQueryExecution.WindowExecution().execute(result);

			assertAll(() -> assertTrue(window.hasPosition(2), "the last index is resumable"),
					() -> assertFalse(window.hasPosition(0),
							"earlier indices are not resumable, and must not throw here"),
					() -> assertFalse(window.hasPosition(1)));
		}
	}

	@Nested
	@DisplayName("Mapping context validation")
	class MappingContextTests {

		@SecondaryIndex(name = "GSI1", tableName = TABLE_NAME)
		@SortKeyTemplate(value = "MATCH#{matchId}", column = "gsi1sk")
		static class TemplatedView {
			@PartitionKey
			@Column("gsi1pk")
			String collectionKey;
			@SortKey
			@Column("gsi1sk")
			String itemKey;
			String matchId;
		}

		@Test
		@DisplayName("an index view may not declare a @SortKeyTemplate")
		void anIndexViewMayNotDeclareASortKeyTemplate() {
			DynamoDbMappingContext ctx = new DynamoDbMappingContext();

			Exception thrown = assertThrows(Exception.class,
					() -> ctx.getRequiredPersistentEntity(TemplatedView.class));

			Throwable root = thrown;
			while (root.getCause() != null) {
				root = root.getCause();
			}
			assertTrue(root.getMessage().contains("must not declare @SortKeyTemplate"),
					"a read-only view cannot compose a written sort key, got: " + root.getMessage());
		}
	}
}
