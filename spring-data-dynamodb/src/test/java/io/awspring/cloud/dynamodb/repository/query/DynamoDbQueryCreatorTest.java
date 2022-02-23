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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.parser.PartTree;

public class DynamoDbQueryCreatorTest {

	@Table(tableName = "single_sort_key")
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

		public Entity findByRoundIn(java.util.List<String> rounds) {
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
				new org.springframework.data.repository.query.DefaultParameters(
						org.springframework.data.repository.query.ParametersSource.of(resolveMethod(methodName, args))),
				args);
		return new DynamoDbQueryCreator(tree, accessor, context, Entity.class).createQuery();
	}

	private static java.lang.reflect.Method resolveMethod(String methodName, Object... args) {
		Class<?>[] paramTypes = new Class<?>[args.length];
		for (int i = 0; i < args.length; i++) {
			paramTypes[i] = args[i] instanceof java.util.List ? java.util.List.class : String.class;
		}
		try {
			return Entity.class.getMethod(methodName, paramTypes);
		}
		catch (NoSuchMethodException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void singlePartitionEqualitySelectsBaseTable() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByPk", "pk-1");

		assertFalse(spec.requiresScan());
		assertEquals("", spec.indexName());
		assertEquals("pk-1", spec.partitionEquals().get("pk"));
		assertTrue(spec.sortConditions().isEmpty());
		assertNull(spec.filterExpression());
	}

	@Test
	void partitionAndSortEqualitySelectsBaseTableWithBothConditions() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkAndSk", "pk-1", "sk-1");

		assertFalse(spec.requiresScan());
		assertEquals("", spec.indexName());
		assertEquals("pk-1", spec.partitionEquals().get("pk"));
		assertEquals(1, spec.sortConditions().size());
		assertEquals("sk", spec.sortConditions().get(0).columnName());
		assertEquals("sk-1", spec.sortConditions().get(0).value());
		assertNull(spec.filterExpression());
	}

	@Test
	void nonKeyEqualityAfterSortKeyBecomesAFilterFragmentNotAKeyCondition() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkAndSkAndRound", "pk-1", "sk-1", "ACTIVE");

		assertFalse(spec.requiresScan());
		assertEquals("", spec.indexName());
		assertEquals("pk-1", spec.partitionEquals().get("pk"));
		assertEquals(1, spec.sortConditions().size());
		assertEquals("sk", spec.sortConditions().get(0).columnName());
		assertTrue(spec.filterExpression() != null && spec.filterExpression().contains("="));
	}

	@Test
	void equalityOnANonKeyAttributeAloneFallsBackToScan() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByRound", "ACTIVE");

		assertTrue(spec.requiresScan());
		assertNull(spec.indexName());
		assertTrue(spec.partitionEquals().isEmpty());
		assertTrue(spec.filterExpression() != null && spec.filterExpression().contains("="));
	}

	@Test
	void orAcrossDifferentPropertiesIsNeverPartOfTheKeyConditionEvenWhenOneSideIsAPartitionKey() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkOrRound", "pk-1", "ACTIVE");

		assertTrue(spec.requiresScan());
		assertTrue(spec.filterExpression() != null && spec.filterExpression().contains("OR"));
	}

	@Test
	void inequalityOnThePartitionKeyIsNeverAKeyConditionEvenAsTheOnlyPredicate() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkGreaterThan", "pk-1");

		assertTrue(spec.requiresScan());
		assertTrue(spec.filterExpression() != null && spec.filterExpression().contains(">"));
	}

	@Test
	void unsupportedKeywordThrowsRatherThanSilentlyMistranslating() {
		assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,
				() -> createSpec(newContext(), "findByPkEndingWith", "pk-1"));
	}

	@Test
	void filterFragmentValuesAreThreadedIntoExpressionAttributeValuesNotDiscarded() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByRound", "ACTIVE");

		assertTrue(spec.requiresScan());
		String fragment = spec.filterExpression();
		String namePlaceholder = fragment.substring(0, fragment.indexOf(' '));
		String valuePlaceholder = fragment.substring(fragment.indexOf('=') + 2);
		assertEquals("round", spec.expressionAttributeNames().get(namePlaceholder));
		assertEquals("ACTIVE", spec.expressionAttributeValues().get(valuePlaceholder));
	}

	@Test
	void keyConditionEqualityAtomsDoNotNeedExpressionAttributeValuesTheyBindRawValuesDirectly() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkAndSk", "pk-1", "sk-1");

		assertTrue(spec.expressionAttributeValues().isEmpty());
		assertTrue(spec.expressionAttributeNames().isEmpty());
	}

	@Test
	void demotedEqualityAtomStillGetsItsExpressionAttributeValueEvenThoughItStartedAsAKeyCandidate() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkAndSkAndRound", "pk-1", "sk-1", "ACTIVE");

		assertFalse(spec.filterExpression() == null);
		boolean foundActiveValue = spec.expressionAttributeValues().containsValue("ACTIVE");
		assertTrue(foundActiveValue);
	}

	@Test
	void orBranchFragmentValuesAreAlsoThreadedNotJustTheFragmentText() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByPkOrRound", "pk-1", "ACTIVE");

		assertTrue(spec.requiresScan());
		assertTrue(spec.expressionAttributeValues().containsValue("pk-1"));
		assertTrue(spec.expressionAttributeValues().containsValue("ACTIVE"));
	}

	@Test
	void inExpandsTheBoundCollectionIntoOnePlaceholderPerElementRatherThanOneForTheWholeList() {
		DynamoDbQuerySpec spec = createSpec(newContext(), "findByRoundIn",
				java.util.List.of("ACTIVE", "PENDING", "CLOSED"));

		assertTrue(spec.requiresScan());
		String fragment = spec.filterExpression();
		assertTrue(fragment.contains(" IN ("));
		long placeholderCount = spec.expressionAttributeValues().values().stream().filter(v -> v instanceof String)
				.count();
		assertEquals(3, placeholderCount);
		assertTrue(spec.expressionAttributeValues().containsValue("ACTIVE"));
		assertTrue(spec.expressionAttributeValues().containsValue("PENDING"));
		assertTrue(spec.expressionAttributeValues().containsValue("CLOSED"));
		assertFalse(spec.expressionAttributeValues().values().stream().anyMatch(v -> v instanceof java.util.List));
	}
}
