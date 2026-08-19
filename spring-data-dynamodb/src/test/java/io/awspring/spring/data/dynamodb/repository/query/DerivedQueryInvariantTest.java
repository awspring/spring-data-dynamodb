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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.repository.query.DefaultParameters;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.ParametersSource;
import org.springframework.data.repository.query.parser.PartTree;

@DisplayName("Derived query structural invariants")
class DerivedQueryInvariantTest {

	private static final String TABLE_NAME = "invariant_probe";
	private static final String PK = "P";
	private static final String SK_LOW = "S1";
	private static final String SK_HIGH = "S9";
	private static final String SK_MID = "S5";
	private static final String ROUND_LOW = "R1";
	private static final String ROUND_HIGH = "R9";
	private static final String ROUND_MID = "R5";
	private static final String REGION = "eu";
	private static final String SK_PREFIX = "MATCH#";
	private static final String ROUND_SUBSTRING = "final";

	@Table(tableName = TABLE_NAME)
	static class Probe {

		@PartitionKey
		String pk;

		@SortKey
		String sk;

		String round;

		String region;

		Boolean active;
	}

	interface Queries {

		Probe findByPk(String pk);

		Probe findByPkAndSk(String pk, String sk);

		Probe findByPkAndSkBetween(String pk, String from, String to);

		Probe findByPkAndSkGreaterThan(String pk, String sk);

		Probe findByPkAndSkGreaterThanEqual(String pk, String sk);

		Probe findByPkAndSkLessThan(String pk, String sk);

		Probe findByPkAndSkLessThanEqual(String pk, String sk);

		Probe findByPkAndSkStartingWith(String pk, String prefix);

		Probe findByPkAndRoundBetween(String pk, String from, String to);

		Probe findByPkAndRoundGreaterThan(String pk, String round);

		Probe findByPkAndRoundNot(String pk, String round);

		Probe findByPkAndRoundContaining(String pk, String round);

		Probe findByPkAndRoundNotContaining(String pk, String round);

		Probe findByPkAndRoundIn(String pk, List<String> rounds);

		Probe findByPkAndRoundNotIn(String pk, List<String> rounds);

		Probe findByPkAndRoundIsNull(String pk);

		Probe findByPkAndRoundIsNotNull(String pk);

		Probe findByPkAndActiveTrue(String pk);

		Probe findByPkAndActiveFalse(String pk);

		Probe findByPkAndRoundAndRegion(String pk, String round, String region);

		Probe findByPkAndRoundBetweenAndRegion(String pk, String from, String to, String region);

		Probe findByRound(String round);

		Probe findByRoundAndRegion(String round, String region);
	}

	static List<Arguments> allSupportedKeywords() {
		return List.of(Arguments.of("findByPk", new Object[] { PK }),
				Arguments.of("findByPkAndSk", new Object[] { PK, "S" }),
				Arguments.of("findByPkAndSkBetween", new Object[] { PK, SK_LOW, SK_HIGH }),
				Arguments.of("findByPkAndSkGreaterThan", new Object[] { PK, SK_MID }),
				Arguments.of("findByPkAndSkGreaterThanEqual", new Object[] { PK, SK_MID }),
				Arguments.of("findByPkAndSkLessThan", new Object[] { PK, SK_MID }),
				Arguments.of("findByPkAndSkLessThanEqual", new Object[] { PK, SK_MID }),
				Arguments.of("findByPkAndSkStartingWith", new Object[] { PK, SK_PREFIX }),
				Arguments.of("findByPkAndRoundBetween", new Object[] { PK, ROUND_LOW, ROUND_HIGH }),
				Arguments.of("findByPkAndRoundGreaterThan", new Object[] { PK, ROUND_MID }),
				Arguments.of("findByPkAndRoundNot", new Object[] { PK, ROUND_MID }),
				Arguments.of("findByPkAndRoundContaining", new Object[] { PK, ROUND_SUBSTRING }),
				Arguments.of("findByPkAndRoundNotContaining", new Object[] { PK, ROUND_SUBSTRING }),
				Arguments.of("findByPkAndRoundIn", new Object[] { PK, List.of(ROUND_LOW, "R2", "R3") }),
				Arguments.of("findByPkAndRoundNotIn", new Object[] { PK, List.of(ROUND_LOW, "R2") }),
				Arguments.of("findByPkAndRoundIsNull", new Object[] { PK }),
				Arguments.of("findByPkAndRoundIsNotNull", new Object[] { PK }),
				Arguments.of("findByPkAndActiveTrue", new Object[] { PK }),
				Arguments.of("findByPkAndActiveFalse", new Object[] { PK }),
				Arguments.of("findByPkAndRoundAndRegion", new Object[] { PK, ROUND_MID, REGION }),
				Arguments.of("findByPkAndRoundBetweenAndRegion", new Object[] { PK, ROUND_LOW, ROUND_HIGH, REGION }),
				Arguments.of("findByRound", new Object[] { ROUND_MID }),
				Arguments.of("findByRoundAndRegion", new Object[] { ROUND_MID, REGION }));
	}

	@Nested
	@DisplayName("Full keyword sweep (parameterized)")
	class KeywordSweepTests {

		@ParameterizedTest(name = "{0}")
		@MethodSource("io.awspring.cloud.dynamodb.repository.query.DerivedQueryInvariantTest#allSupportedKeywords")
		@DisplayName("every supported keyword produces a spec DynamoDB would accept")
		void everySupportedKeywordProducesAnInternallyConsistentSpec(String methodName, Object[] arguments) {
			// Act
			DynamoDbQuerySpec spec = createSpec(methodName, arguments);

			// Assert
			ExpressionInvariants.assertAllInvariants(spec, flatten(arguments));
		}
	}

	@Nested
	@DisplayName("BETWEEN range binding")
	class BetweenRangeTests {

		@Test
		@DisplayName("a BETWEEN range binds both bounds to distinct slots")
		void betweenBindsBothBoundsSeparately() {
			// Act
			DynamoDbQuerySpec spec = createSpec("findByPkAndRoundBetween", PK, ROUND_LOW, ROUND_HIGH);

			// Assert
			assertAll(
					() -> assertTrue(ExpressionInvariants.reachableValues(spec).contains(ROUND_LOW),
							"the lower bound must survive translation"),
					() -> assertTrue(ExpressionInvariants.reachableValues(spec).contains(ROUND_HIGH),
							"the upper bound must survive translation"),
					() -> assertEquals(2, spec.expressionAttributeValues().values().stream().distinct().count(),
							"a BETWEEN over two distinct bounds must bind two distinct values"));
		}

		@Test
		@DisplayName("a sort-key BETWEEN becomes a key condition, not a filter")
		void sortKeyBetweenBecomesAKeyCondition() {
			// Act
			DynamoDbQuerySpec spec = createSpec("findByPkAndSkBetween", PK, SK_LOW, SK_HIGH);

			// Assert
			assertAll(() -> assertFalse(spec.requiresScan(), "a pinned partition key must never fall back to a Scan"),
					() -> assertEquals(1, spec.sortConditions().size(),
							"the sort-key range belongs in the KeyConditionExpression, "
									+ "where DynamoDB can use it to narrow the read; as a FilterExpression it would be "
									+ "applied only after Limit, silently dropping rows"));

			DynamoDbQuerySpec.SortCondition condition = spec.sortConditions().get(0);
			assertAll(() -> Assertions.assertEquals(DynamoDbQuerySpec.SortCondition.Op.BETWEEN, condition.op()),
					() -> assertEquals(SK_LOW, condition.value(), "lower bound"),
					() -> assertEquals(SK_HIGH, condition.rangeEnd(), "upper bound"));
		}

		@Test
		@DisplayName("both BETWEEN bounds survive even when a filter follows them")
		void betweenBoundsSurviveAlongsideAdditionalFilters() {
			// Act
			DynamoDbQuerySpec spec = createSpec("findByPkAndRoundBetweenAndRegion", PK, ROUND_LOW, ROUND_HIGH, REGION);

			// Assert
			ExpressionInvariants.assertAllInvariants(spec, PK, ROUND_LOW, ROUND_HIGH, REGION);
			assertTrue(ExpressionInvariants.reachableValues(spec).containsAll(List.of(ROUND_LOW, ROUND_HIGH, REGION)),
					"a following condition must not overwrite either range bound");
		}

		@Test
		@DisplayName("repeated conditions on one property keep independent slots")
		void repeatedConditionsOnOnePropertyKeepIndependentSlots() {
			// Act
			DynamoDbQuerySpec spec = createSpec("findByPkAndRoundBetween", PK, "SAME", "SAME");

			// Assert
			assertAll(
					() -> assertEquals(2, spec.expressionAttributeValues().size(),
							"two operands require two slots even when the bound values happen to be equal"),
					() -> ExpressionInvariants.assertAllInvariants(spec, PK, "SAME"));
		}
	}

	@Nested
	@DisplayName("Scan vs Query classification")
	class ScanClassificationTests {

		@Test
		@DisplayName("an unpinned partition key is the only reason to Scan")
		void onlyAnUnpinnedPartitionKeyCausesAScan() {
			assertAll(() -> assertFalse(createSpec("findByPk", PK).requiresScan(), "pinned partition key -> Query"),
					() -> assertFalse(createSpec("findByPkAndRoundGreaterThan", PK, ROUND_MID).requiresScan(),
							"pinned partition key plus a non-key filter is still a Query"),
					() -> assertTrue(createSpec("findByRound", ROUND_MID).requiresScan(),
							"no partition key pinned -> Scan"));
		}
	}

	@Nested
	@DisplayName("IN list handling")
	class InListTests {

		@Test
		@DisplayName("an empty IN list cannot produce invalid syntax and must match nothing")
		void anEmptyInListCollapsesToAConstantThatMatchesNothing() {
			// Act
			DynamoDbQuerySpec spec = createSpec("findByPkAndRoundIn", PK, List.of());

			// Assert
			String filter = spec.filterExpression();
			assertAll(() -> assertNotNull(filter, "an empty IN must still render a filter"),
					() -> assertFalse(filter.contains("IN ()"), "'IN ()' is a syntax error DynamoDB rejects outright"),
					() -> assertFalse(filter.matches(".*IN\\s*\\(\\s*\\).*"),
							"an empty operand list must never reach DynamoDB"));
			ExpressionInvariants.assertAllInvariants(spec, PK);
		}

		@Test
		@DisplayName("a non-empty IN binds one distinct slot per element")
		void inBindsEveryElementSeparately() {
			// Act
			DynamoDbQuerySpec spec = createSpec("findByPkAndRoundIn", PK, List.of(ROUND_LOW, "R2", "R3"));

			// Assert
			ExpressionInvariants.assertAllInvariants(spec, PK, ROUND_LOW, "R2", "R3");
			assertTrue(ExpressionInvariants.reachableValues(spec).containsAll(List.of(ROUND_LOW, "R2", "R3")),
					"every IN element must reach DynamoDB");
		}
	}

	// ──────────────────────────────────────────────────────────────────────────────
	// Helpers
	// ──────────────────────────────────────────────────────────────────────────────

	private static DynamoDbQuerySpec createSpec(String methodName, Object... arguments) {
		DynamoDbMappingContext context = new DynamoDbMappingContext();
		context.getRequiredPersistentEntity(Probe.class);

		PartTree tree = new PartTree(methodName, Probe.class);
		Method method = resolve(methodName);
		ParametersParameterAccessor accessor = new ParametersParameterAccessor(
				new DefaultParameters(ParametersSource.of(method)), arguments);

		return new DynamoDbQueryCreator(tree, accessor, context, Probe.class).createQuery();
	}

	private static Method resolve(String methodName) {
		for (Method candidate : Queries.class.getDeclaredMethods()) {
			if (candidate.getName().equals(methodName)) {
				return candidate;
			}
		}
		throw new IllegalStateException("no such query method declared on Queries: " + methodName);
	}

	private static Object[] flatten(Object[] arguments) {
		List<Object> flattened = new java.util.ArrayList<>();
		for (Object argument : arguments) {
			if (argument instanceof List<?> list) {
				flattened.addAll(list);
			}
			else {
				flattened.add(argument);
			}
		}
		return flattened.toArray();
	}
}
