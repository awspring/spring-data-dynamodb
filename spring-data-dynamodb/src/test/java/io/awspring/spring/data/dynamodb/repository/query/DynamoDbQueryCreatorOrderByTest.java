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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.repository.query.DefaultParameters;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.ParametersSource;
import org.springframework.data.repository.query.parser.PartTree;

@DisplayName("DynamoDbQueryCreator OrderBy handling")
class DynamoDbQueryCreatorOrderByTest {

	private static final String TABLE_NAME = "single_sort_key";
	private static final String PK_VALUE = "pk-1";
	private static final String ROUND_ACTIVE = "ACTIVE";
	private static final String PARTITION_KEY = "cust-1";
	private static final int YEAR = 2024;

	@Table(tableName = TABLE_NAME)
	static class Entity {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String round;

		public Entity() {
		}

		public Entity findByPkOrderBySkAsc(String pk) {
			return null;
		}

		public Entity findByPkOrderBySkDesc(String pk) {
			return null;
		}

		public Entity findByPkOrderByRoundAsc(String pk) {
			return null;
		}

		public Entity findByRoundOrderByRoundAsc(String round) {
			return null;
		}

		public Entity findByPkOrderBySkAscRoundDesc(String pk) {
			return null;
		}
	}

	@Table(tableName = "orders")
	@SortKeyTemplate("MATCH#{year}#{round}")
	static class Match {
		@PartitionKey
		String tournamentId;
		int year;
		String round;

		public Match() {
		}

		public Match findByTournamentIdAndYearOrderByRoundAsc(String tournamentId, int year) {
			return null;
		}
	}

	private static DynamoDbMappingContext newContext(Class<?> type) {
		DynamoDbMappingContext context = new DynamoDbMappingContext();
		context.getRequiredPersistentEntity(type);
		return context;
	}

	private static <T> DynamoDbQuerySpec createSpec(Class<T> type, String methodName, Object... args) {
		DynamoDbMappingContext context = newContext(type);
		PartTree tree = new PartTree(methodName, type);
		ParametersParameterAccessor accessor = new ParametersParameterAccessor(
				new DefaultParameters(ParametersSource.of(resolveMethod(type, methodName, args))), args);
		return new DynamoDbQueryCreator(tree, accessor, context, type).createQuery();
	}

	private static java.lang.reflect.Method resolveMethod(Class<?> type, String methodName, Object... args) {
		Class<?>[] paramTypes = new Class<?>[args.length];
		for (int i = 0; i < args.length; i++) {
			paramTypes[i] = args[i] instanceof Integer ? int.class : String.class;
		}
		try {
			return type.getMethod(methodName, paramTypes);
		}
		catch (NoSuchMethodException e) {
			throw new IllegalStateException(e);
		}
	}

	@Nested
	@DisplayName("Valid OrderBy on the sort key")
	class ValidOrderByTests {

		@Test
		@DisplayName("OrderBy sort key ASC sets scanIndexForward=true")
		void orderByTheSortKeyAscendingSetsScanIndexForwardTrue() {
			// Act
			DynamoDbQuerySpec spec = createSpec(Entity.class, "findByPkOrderBySkAsc", PK_VALUE);

			// Assert
			assertAll(() -> assertFalse(spec.requiresScan()), () -> assertTrue(spec.scanIndexForward()));
		}

		@Test
		@DisplayName("OrderBy sort key DESC sets scanIndexForward=false")
		void orderByTheSortKeyDescendingSetsScanIndexForwardFalse() {
			// Act
			DynamoDbQuerySpec spec = createSpec(Entity.class, "findByPkOrderBySkDesc", PK_VALUE);

			// Assert
			assertAll(() -> assertFalse(spec.requiresScan()), () -> assertFalse(spec.scanIndexForward()));
		}
	}

	@Nested
	@DisplayName("Rejected OrderBy scenarios")
	class RejectedOrderByTests {

		@Test
		@DisplayName("OrderBy a non-sort-key property throws")
		void orderByAPropertyThatIsNotTheSortKeyThrows() {
			InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
					() -> createSpec(Entity.class, "findByPkOrderByRoundAsc", PK_VALUE));

			assertAll(() -> assertTrue(ex.getMessage().contains("round")),
					() -> assertTrue(ex.getMessage().contains("sk")));
		}

		@Test
		@DisplayName("OrderBy on a scan fallback method throws")
		void orderByOnAScanFallbackMethodThrows() {
			InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
					() -> createSpec(Entity.class, "findByRoundOrderByRoundAsc", ROUND_ACTIVE));

			assertTrue(ex.getMessage().contains("Scan"));
		}

		@Test
		@DisplayName("multi-property OrderBy throws")
		void multiPropertyOrderByThrows() {
			InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
					() -> createSpec(Entity.class, "findByPkOrderBySkAscRoundDesc", PK_VALUE));

			assertTrue(ex.getMessage().contains("single ScanIndexForward"));
		}

		@Test
		@DisplayName("OrderBy a placeholder property of a @SortKeyTemplate throws")
		void orderByAPlaceholderPropertyOfATemplateColumnThrows() {
			InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
					() -> createSpec(Match.class, "findByTournamentIdAndYearOrderByRoundAsc", PARTITION_KEY, YEAR));

			assertAll(() -> assertTrue(ex.getMessage().contains("round")),
					() -> assertTrue(ex.getMessage().contains("@SortKeyTemplate")));
		}
	}
}
