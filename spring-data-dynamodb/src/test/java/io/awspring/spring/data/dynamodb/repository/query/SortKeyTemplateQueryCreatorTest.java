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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.DefaultParameters;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.ParametersSource;
import org.springframework.data.repository.query.parser.PartTree;

@DisplayName("SortKeyTemplateQueryCreator")
class SortKeyTemplateQueryCreatorTest {

	private static final String TABLE_NAME = "orders";
	private static final String SORT_KEY_TEMPLATE = "MATCH#{year}#{round}";
	private static final String PARTITION_KEY = "cust-1";
	private static final int YEAR = 2024;
	private static final String ROUND = "QUARTERFINAL";
	private static final String EXPECTED_PREFIX = "MATCH#2024#";
	private static final String EXPECTED_FULL_KEY = "MATCH#2024#QUARTERFINAL";
	private static final String SORT_KEY_COLUMN = "sk";
	private static final String PARTITION_FIELD = "tournamentId";

	@Table(tableName = TABLE_NAME)
	@SortKeyTemplate(SORT_KEY_TEMPLATE)
	static class Match {
		@PartitionKey
		String tournamentId;
		int year;
		String round;

		public Match() {
		}

		public Match findByTournamentId(String tournamentId) {
			return null;
		}

		public Match findByTournamentIdAndYear(String tournamentId, int year) {
			return null;
		}

		public Match findByTournamentIdAndYearAndRound(String tournamentId, int year, String round) {
			return null;
		}
	}

	private static DynamoDbMappingContext newContext() {
		DynamoDbMappingContext context = new DynamoDbMappingContext();
		context.getRequiredPersistentEntity(Match.class);
		return context;
	}

	private static DynamoDbQuerySpec createSpec(DynamoDbMappingContext context, String methodName, Object... args) {
		PartTree tree = new PartTree(methodName, Match.class);
		ParametersParameterAccessor accessor = new ParametersParameterAccessor(
				new DefaultParameters(ParametersSource.of(resolveMethod(methodName, args))), args);
		return new DynamoDbQueryCreator(tree, accessor, context, Match.class).createQuery();
	}

	private static java.lang.reflect.Method resolveMethod(String methodName, Object... args) {
		Class<?>[] paramTypes = new Class<?>[args.length];
		for (int i = 0; i < args.length; i++) {
			paramTypes[i] = args[i] instanceof Integer ? int.class : String.class;
		}
		try {
			return Match.class.getMethod(methodName, paramTypes);
		}
		catch (NoSuchMethodException e) {
			throw new IllegalStateException(e);
		}
	}

	@Nested
	@DisplayName("Partial placeholder binding")
	class PartialPlaceholderBinding {

		@Test
		@DisplayName("leading placeholder subset produces a begins_with condition on the composed prefix")
		void leadingPlaceholderSubsetProducesABeginsWithConditionOnTheComposedPrefix() {
			// Arrange
			DynamoDbMappingContext context = newContext();

			// Act
			DynamoDbQuerySpec spec = createSpec(context, "findByTournamentIdAndYear", PARTITION_KEY, YEAR);

			// Assert
			DynamoDbQuerySpec.SortCondition condition = spec.sortConditions().get(0);
			assertAll(() -> assertFalse(spec.requiresScan()), () -> assertEquals("", spec.indexName()),
					() -> assertEquals(PARTITION_KEY, spec.partitionEquals().get(PARTITION_FIELD)),
					() -> assertEquals(1, spec.sortConditions().size()),
					() -> assertEquals(SORT_KEY_COLUMN, condition.columnName()),
					() -> assertEquals(DynamoDbQuerySpec.SortCondition.Op.BEGINS_WITH, condition.op()),
					() -> assertEquals(EXPECTED_PREFIX, condition.value()), () -> assertNull(spec.filterExpression()));
		}
	}

	@Nested
	@DisplayName("Full placeholder binding")
	class FullPlaceholderBinding {

		@Test
		@DisplayName("all placeholders bound produces an exact EQ condition on the fully composed string")
		void allPlaceholdersBoundProducesAnExactEqConditionOnTheFullyComposedString() {
			// Arrange
			DynamoDbMappingContext context = newContext();

			// Act
			DynamoDbQuerySpec spec = createSpec(context, "findByTournamentIdAndYearAndRound", PARTITION_KEY, YEAR,
					ROUND);

			// Assert
			DynamoDbQuerySpec.SortCondition condition = spec.sortConditions().get(0);
			assertAll(() -> assertFalse(spec.requiresScan()), () -> assertEquals("", spec.indexName()),
					() -> assertEquals(PARTITION_KEY, spec.partitionEquals().get(PARTITION_FIELD)),
					() -> assertEquals(1, spec.sortConditions().size()),
					() -> assertEquals(SORT_KEY_COLUMN, condition.columnName()),
					() -> assertEquals(DynamoDbQuerySpec.SortCondition.Op.EQ, condition.op()),
					() -> assertEquals(EXPECTED_FULL_KEY, condition.value()),
					() -> assertNull(spec.filterExpression()));
		}
	}

	@Nested
	@DisplayName("Partition key only")
	class PartitionKeyOnly {

		@Test
		@DisplayName("partition key alone produces a partition-only spec with no sort condition")
		void partitionKeyAloneProducesAPartitionOnlySpecWithNoSortCondition() {
			// Arrange
			DynamoDbMappingContext context = newContext();

			// Act
			DynamoDbQuerySpec spec = createSpec(context, "findByTournamentId", PARTITION_KEY);

			// Assert
			assertAll(() -> assertFalse(spec.requiresScan()), () -> assertEquals("", spec.indexName()),
					() -> assertEquals(PARTITION_KEY, spec.partitionEquals().get(PARTITION_FIELD)),
					() -> assertTrue(spec.sortConditions().isEmpty()), () -> assertNull(spec.filterExpression()));
		}
	}
}
