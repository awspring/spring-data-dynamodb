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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.parser.PartTree;

public class DynamoDbQueryCreatorOrderByTest {

	@Table(tableName = "single_sort_key")
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
				new org.springframework.data.repository.query.DefaultParameters(
						org.springframework.data.repository.query.ParametersSource
								.of(resolveMethod(type, methodName, args))),
				args);
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

	@Test
	void orderByTheSortKeyAscendingSetsScanIndexForwardTrue() {
		DynamoDbQuerySpec spec = createSpec(Entity.class, "findByPkOrderBySkAsc", "pk-1");

		assertFalse(spec.requiresScan());
		assertTrue(spec.scanIndexForward());
	}

	@Test
	void orderByTheSortKeyDescendingSetsScanIndexForwardFalse() {
		DynamoDbQuerySpec spec = createSpec(Entity.class, "findByPkOrderBySkDesc", "pk-1");

		assertFalse(spec.requiresScan());
		assertFalse(spec.scanIndexForward());
	}

	@Test
	void orderByAPropertyThatIsNotTheSortKeyThrows() {
		InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
				() -> createSpec(Entity.class, "findByPkOrderByRoundAsc", "pk-1"));
		assertTrue(ex.getMessage().contains("round"));
		assertTrue(ex.getMessage().contains("sk"));
	}

	@Test
	void orderByOnAScanFallbackMethodThrows() {
		InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
				() -> createSpec(Entity.class, "findByRoundOrderByRoundAsc", "ACTIVE"));
		assertTrue(ex.getMessage().contains("Scan"));
	}

	@Test
	void multiPropertyOrderByThrows() {
		InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
				() -> createSpec(Entity.class, "findByPkOrderBySkAscRoundDesc", "pk-1"));
		assertTrue(ex.getMessage().contains("single ScanIndexForward"));
	}

	@Test
	void orderByAPlaceholderPropertyOfATemplateColumnThrows() {
		InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
				() -> createSpec(Match.class, "findByTournamentIdAndYearOrderByRoundAsc", "cust-1", 2024));
		assertTrue(ex.getMessage().contains("round"));
		assertTrue(ex.getMessage().contains("@SortKeyTemplate"));
	}
}
