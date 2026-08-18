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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.repository.query.DefaultParameters;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.ParametersSource;
import org.springframework.data.repository.query.parser.PartTree;

@DisplayName("DynamoDbQueryCreator")
class DynamoDbQueryCreatorTest {

	private static final String TABLE_NAME = "single_sort_key";
	private static final String PK_VALUE = "pk-1";
	private static final String SK_VALUE = "sk-1";
	private static final String ROUND_ACTIVE = "ACTIVE";
	private static final String ROUND_PENDING = "PENDING";
	private static final String ROUND_CLOSED = "CLOSED";
	private static final String BASE_TABLE_INDEX = "";
	private static final int EXPECTED_IN_PLACEHOLDER_COUNT = 3;

	@Table(tableName = TABLE_NAME)
	static class Entity {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String round;

		public Entity() {
		}

		public Entity findByPk(String pk) {
			return null;
		}

		public Entity findByPkAndSk(String pk, String sk) {
			return null;
		}

		public Entity findByPkAndSkAndRound(String pk, String sk, String round) {
			return null;
		}

		public Entity findByRound(String round) {
			return null;
		}

		public Entity findByPkOrRound(String pk, String round) {
			return null;
		}

		public Entity findByPkGreaterThan(String pk) {
			return null;
		}

		public Entity findByPkEndingWith(String pk) {
			return null;
		}

		public Entity findByRoundIn(List<String> rounds) {
			return null;
		}
	}

	private static DynamoDbMappingContext newContext() {
		DynamoDbMappingContext context = new DynamoDbMappingContext();
		context.getRequiredPersistentEntity(Entity.class);
		return context;
	}

	private static DynamoDbQuerySpec createSpec(DynamoDbMappingContext context, String methodName, Object... args) {
		PartTree tree = new PartTree(methodName, Entity.class);
		ParametersParameterAccessor accessor = new ParametersParameterAccessor(
				new DefaultParameters(ParametersSource.of(resolveMethod(methodName, args))), args);
		return new DynamoDbQueryCreator(tree, accessor, context, Entity.class).createQuery();
	}

	private static Method resolveMethod(String methodName, Object... args) {
		Class<?>[] paramTypes = new Class<?>[args.length];
		for (int i = 0; i < args.length; i++) {
			paramTypes[i] = args[i] instanceof List ? List.class : String.class;
		}
		try {
			return Entity.class.getMethod(methodName, paramTypes);
		}
		catch (NoSuchMethodException e) {
			throw new IllegalStateException(e);
		}
	}

	@Nested
	@DisplayName("Partition key equality (base table)")
	class PartitionKeyEqualityTests {

		@Test
		@DisplayName("single partition equality selects the base table with no sort condition")
		void singlePartitionEqualitySelectsBaseTable() {
			// Act
			DynamoDbQuerySpec spec = createSpec(newContext(), "findByPk", PK_VALUE);

			// Assert
			assertAll(() -> assertFalse(spec.requiresScan()), () -> assertEquals(BASE_TABLE_INDEX, spec.indexName()),
					() -> assertEquals(PK_VALUE, spec.partitionEquals().get("pk")),
					() -> assertTrue(spec.sortConditions().isEmpty()), () -> assertNull(spec.filterExpression()));
		}

		@Test
		@DisplayName("partition + sort equality selects the base table with both conditions")
		void partitionAndSortEqualitySelectsBaseTableWithBothConditions() {
			// Act
			DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkAndSk", PK_VALUE, SK_VALUE);

			// Assert
			assertAll(() -> assertFalse(spec.requiresScan()), () -> assertEquals(BASE_TABLE_INDEX, spec.indexName()),
					() -> assertEquals(PK_VALUE, spec.partitionEquals().get("pk")),
					() -> assertEquals(1, spec.sortConditions().size()),
					() -> assertEquals("sk", spec.sortConditions().get(0).columnName()),
					() -> assertEquals(SK_VALUE, spec.sortConditions().get(0).value()),
					() -> assertNull(spec.filterExpression()));
		}

		@Test
		@DisplayName("key condition atoms bind raw values directly without ExpressionAttributeValues")
		void keyConditionAtomsBindRawValues() {
			// Act
			DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkAndSk", PK_VALUE, SK_VALUE);

			// Assert
			assertAll(() -> assertTrue(spec.expressionAttributeValues().isEmpty()),
					() -> assertTrue(spec.expressionAttributeNames().isEmpty()));
		}
	}

	@Nested
	@DisplayName("Non-key predicates and filter expressions")
	class FilterExpressionTests {

		@Test
		@DisplayName("a non-key attribute after the sort key becomes a filter fragment")
		void nonKeyEqualityAfterSortKeyBecomesFilterFragment() {
			// Act
			DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkAndSkAndRound", PK_VALUE, SK_VALUE,
					ROUND_ACTIVE);

			// Assert
			assertAll(() -> assertFalse(spec.requiresScan()), () -> assertEquals(BASE_TABLE_INDEX, spec.indexName()),
					() -> assertEquals(PK_VALUE, spec.partitionEquals().get("pk")),
					() -> assertEquals(1, spec.sortConditions().size()),
					() -> assertEquals("sk", spec.sortConditions().get(0).columnName()),
					() -> assertTrue(spec.filterExpression() != null && spec.filterExpression().contains("=")));
		}

		@Test
		@DisplayName("a non-key equality alone falls back to scan")
		void equalityOnNonKeyAttributeAloneFallsBackToScan() {
			// Act
			DynamoDbQuerySpec spec = createSpec(newContext(), "findByRound", ROUND_ACTIVE);

			// Assert
			assertAll(() -> assertTrue(spec.requiresScan()), () -> assertNull(spec.indexName()),
					() -> assertTrue(spec.partitionEquals().isEmpty()),
					() -> assertTrue(spec.filterExpression() != null && spec.filterExpression().contains("=")));
		}

		@Test
		@DisplayName("filter fragment values are threaded into ExpressionAttributeValues")
		void filterFragmentValuesThreadedIntoExpressionAttributeValues() {
			// Act
			DynamoDbQuerySpec spec = createSpec(newContext(), "findByRound", ROUND_ACTIVE);

			// Assert
			assertTrue(spec.requiresScan());
			String fragment = spec.filterExpression();
			String namePlaceholder = fragment.substring(0, fragment.indexOf(' '));
			String valuePlaceholder = fragment.substring(fragment.indexOf('=') + 2);

			assertAll(() -> assertEquals("round", spec.expressionAttributeNames().get(namePlaceholder)),
					() -> assertEquals(ROUND_ACTIVE, spec.expressionAttributeValues().get(valuePlaceholder)));
		}

		@Test
		@DisplayName("a demoted equality atom still gets its ExpressionAttributeValue")
		void demotedEqualityAtomStillGetsExpressionAttributeValue() {
			// Act
			DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkAndSkAndRound", PK_VALUE, SK_VALUE,
					ROUND_ACTIVE);

			// Assert
			assertAll(() -> assertFalse(spec.filterExpression() == null),
					() -> assertTrue(spec.expressionAttributeValues().containsValue(ROUND_ACTIVE)));
		}
	}

	@Nested
	@DisplayName("OR and inequality operators")
	class OrAndInequalityTests {

		@Test
		@DisplayName("OR across different properties forces a scan even when one side is a partition key")
		void orAcrossDifferentPropertiesForcesScan() {
			// Act
			DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkOrRound", PK_VALUE, ROUND_ACTIVE);

			// Assert
			assertAll(() -> assertTrue(spec.requiresScan()),
					() -> assertTrue(spec.filterExpression() != null && spec.filterExpression().contains("OR")));
		}

		@Test
		@DisplayName("OR branch fragment values are threaded into ExpressionAttributeValues")
		void orBranchFragmentValuesThreadedIntoExpressionAttributeValues() {
			// Act
			DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkOrRound", PK_VALUE, ROUND_ACTIVE);

			// Assert
			assertAll(() -> assertTrue(spec.requiresScan()),
					() -> assertTrue(spec.expressionAttributeValues().containsValue(PK_VALUE)),
					() -> assertTrue(spec.expressionAttributeValues().containsValue(ROUND_ACTIVE)));
		}

		@Test
		@DisplayName("inequality on the partition key forces a scan")
		void inequalityOnPartitionKeyForcesScan() {
			// Act
			DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkGreaterThan", PK_VALUE);

			// Assert
			assertAll(() -> assertTrue(spec.requiresScan()),
					() -> assertTrue(spec.filterExpression() != null && spec.filterExpression().contains(">")));
		}
	}

	@Nested
	@DisplayName("Unsupported keywords and IN expansion")
	class UnsupportedAndInTests {

		@Test
		@DisplayName("an unsupported keyword throws rather than silently mistranslating")
		void unsupportedKeywordThrows() {
			// Act & Assert
			assertThrows(InvalidDataAccessApiUsageException.class,
					() -> createSpec(newContext(), "findByPkEndingWith", PK_VALUE));
		}

		@Test
		@DisplayName("IN expands the collection into one placeholder per element")
		void inExpandsCollectionIntoOnePlaceholderPerElement() {
			// Act
			DynamoDbQuerySpec spec = createSpec(newContext(), "findByRoundIn",
					List.of(ROUND_ACTIVE, ROUND_PENDING, ROUND_CLOSED));

			// Assert
			assertTrue(spec.requiresScan());
			String fragment = spec.filterExpression();
			assertTrue(fragment.contains(" IN ("));
			long placeholderCount = spec.expressionAttributeValues().values().stream().filter(v -> v instanceof String)
					.count();

			assertAll(() -> assertEquals(EXPECTED_IN_PLACEHOLDER_COUNT, placeholderCount),
					() -> assertTrue(spec.expressionAttributeValues().containsValue(ROUND_ACTIVE)),
					() -> assertTrue(spec.expressionAttributeValues().containsValue(ROUND_PENDING)),
					() -> assertTrue(spec.expressionAttributeValues().containsValue(ROUND_CLOSED)), () -> assertFalse(
							spec.expressionAttributeValues().values().stream().anyMatch(v -> v instanceof List)));
		}
	}
}
