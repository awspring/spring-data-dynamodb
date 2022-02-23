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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.parser.PartTree;

public class SortKeyTemplateQueryCreatorTest {

	@Table(tableName = "orders")
	@SortKeyTemplate("MATCH#{year}#{round}")
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
				new org.springframework.data.repository.query.DefaultParameters(
						org.springframework.data.repository.query.ParametersSource.of(resolveMethod(methodName, args))),
				args);
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

	@Test
	void leadingPlaceholderSubsetProducesABeginsWithConditionOnTheComposedPrefix() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByTournamentIdAndYear", "cust-1", 2024);

		assertFalse(spec.requiresScan());
		assertEquals("", spec.indexName());
		assertEquals("cust-1", spec.partitionEquals().get("tournamentId"));

		assertEquals(1, spec.sortConditions().size());
		DynamoDbQuerySpec.SortCondition condition = spec.sortConditions().get(0);
		assertEquals("sk", condition.columnName());
		assertEquals(DynamoDbQuerySpec.SortCondition.Op.BEGINS_WITH, condition.op());
		assertEquals("MATCH#2024#", condition.value());
		assertNull(spec.filterExpression());
	}

	@Test
	void allPlaceholdersBoundProducesAnExactEqConditionOnTheFullyComposedString() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByTournamentIdAndYearAndRound", "cust-1", 2024,
				"QUARTERFINAL");

		assertFalse(spec.requiresScan());
		assertEquals("", spec.indexName());
		assertEquals("cust-1", spec.partitionEquals().get("tournamentId"));

		assertEquals(1, spec.sortConditions().size());
		DynamoDbQuerySpec.SortCondition condition = spec.sortConditions().get(0);
		assertEquals("sk", condition.columnName());
		assertEquals(DynamoDbQuerySpec.SortCondition.Op.EQ, condition.op());
		assertEquals("MATCH#2024#QUARTERFINAL", condition.value());
		assertNull(spec.filterExpression());
	}

	@Test
	void partitionKeyAloneProducesAPartitionOnlySpecWithNoSortCondition() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByTournamentId", "cust-1");

		assertFalse(spec.requiresScan());
		assertEquals("", spec.indexName());
		assertEquals("cust-1", spec.partitionEquals().get("tournamentId"));
		assertTrue(spec.sortConditions().isEmpty());
		assertNull(spec.filterExpression());
	}
}
