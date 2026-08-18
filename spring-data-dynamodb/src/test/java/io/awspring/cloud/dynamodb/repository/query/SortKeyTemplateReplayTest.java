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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;

@DisplayName("SortKeyTemplate replay through PartTreeDynamoDbQuery")
class SortKeyTemplateReplayTest {

	private static final String TABLE_NAME = "orders";
	private static final String SORT_KEY_TEMPLATE = "MATCH#{year}#{round}";
	private static final String PARTITION_KEY = "cust-1";
	private static final int YEAR = 2024;
	private static final String ROUND = "QUARTERFINAL";
	private static final String EXPECTED_PREFIX = "MATCH#2024#";
	private static final String EXPECTED_FULL_KEY = "MATCH#2024#QUARTERFINAL";
	private static final String EXPECTED_BEGINS_WITH_EXPR = "#tk0 = :tk0 AND begins_with(#tk1, :tk1)";
	private static final String EXPECTED_EQ_EXPR = "#tk0 = :tk0 AND #tk1 = :tk1";

	@Table(tableName = TABLE_NAME)
	@SortKeyTemplate(SORT_KEY_TEMPLATE)
	static class Match {
		@PartitionKey
		String tournamentId;
		int year;
		String round;
	}

	interface MatchRepository extends Repository<Match, String> {
		List<Match> findByTournamentIdAndYear(String tournamentId, int year);

		List<Match> findByTournamentIdAndYearAndRound(String tournamentId, int year, String round);

		List<Match> findByTournamentId(String tournamentId);
	}

	private static PartTreeDynamoDbQuery queryFor(PartTreeDynamoDbQueryReplayTest.CapturingOperations operations,
			String methodName, Class<?>... paramTypes) throws NoSuchMethodException {
		Method method = MatchRepository.class.getMethod(methodName, paramTypes);
		RepositoryMetadata metadata = new DefaultRepositoryMetadata(MatchRepository.class);
		ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		DynamoDbQueryMethod queryMethod = new DynamoDbQueryMethod(method, metadata, projectionFactory, mappingContext);
		return new PartTreeDynamoDbQuery(queryMethod, operations);
	}

	private static PartTreeDynamoDbQueryReplayTest.CapturingOperations operations() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		return new PartTreeDynamoDbQueryReplayTest.CapturingOperations(converter);
	}

	@Nested
	@DisplayName("Leading placeholder subset (begins_with)")
	class BeginsWithTests {

		@Test
		@DisplayName("replays as a begins_with key condition without throwing")
		void leadingPlaceholderSubsetReplaysAsABeginsWithKeyConditionWithoutThrowing() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			PartTreeDynamoDbQuery query = queryFor(operations, "findByTournamentIdAndYear", String.class, int.class);

			// Act
			query.execute(new Object[] { PARTITION_KEY, YEAR });

			// Assert
			assertAll(() -> assertNotNull(operations.lastCapturedRequest),
					() -> assertNull(operations.lastCapturedRequest.getIndexName()),
					() -> assertTrue(
							operations.lastCapturedRequest.getKeyConditionExpression().contains("begins_with")),
					() -> assertTrue(
							operations.lastCapturedRequest.getExpressionAttributeValues().containsValue(PARTITION_KEY)),
					() -> assertTrue(operations.lastCapturedRequest.getExpressionAttributeValues()
							.containsValue(EXPECTED_PREFIX)));
		}

		@Test
		@DisplayName("has exactly the syntax real DynamoDB accepts")
		void beginsWithKeyConditionHasExactlyTheSyntaxRealDynamoDbAccepts() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			PartTreeDynamoDbQuery query = queryFor(operations, "findByTournamentIdAndYear", String.class, int.class);

			// Act
			query.execute(new Object[] { PARTITION_KEY, YEAR });

			// Assert
			assertEquals(EXPECTED_BEGINS_WITH_EXPR, operations.lastCapturedRequest.getKeyConditionExpression());
		}
	}

	@Nested
	@DisplayName("All placeholders bound (exact EQ)")
	class ExactEqTests {

		@Test
		@DisplayName("replays as an exact EQ key condition")
		void allPlaceholdersBoundReplaysAsAnExactEqKeyCondition() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			PartTreeDynamoDbQuery query = queryFor(operations, "findByTournamentIdAndYearAndRound", String.class,
					int.class, String.class);

			// Act
			query.execute(new Object[] { PARTITION_KEY, YEAR, ROUND });

			// Assert
			String keyCondition = operations.lastCapturedRequest.getKeyConditionExpression();
			assertAll(() -> assertNotNull(operations.lastCapturedRequest), () -> assertTrue(keyCondition.contains("=")),
					() -> assertTrue(keyCondition.contains("AND")), () -> assertTrue(operations.lastCapturedRequest
							.getExpressionAttributeValues().containsValue(EXPECTED_FULL_KEY)));
		}

		@Test
		@DisplayName("has exactly the syntax real DynamoDB accepts")
		void fullyBoundEqKeyConditionHasExactlyTheSyntaxRealDynamoDbAccepts() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			PartTreeDynamoDbQuery query = queryFor(operations, "findByTournamentIdAndYearAndRound", String.class,
					int.class, String.class);

			// Act
			query.execute(new Object[] { PARTITION_KEY, YEAR, ROUND });

			// Assert
			assertEquals(EXPECTED_EQ_EXPR, operations.lastCapturedRequest.getKeyConditionExpression());
		}
	}

	@Nested
	@DisplayName("Partition key alone (no sort condition)")
	class PartitionOnlyTests {

		@Test
		@DisplayName("replays with no sort condition at all")
		void partitionKeyAloneReplaysWithNoSortConditionAtAll() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			PartTreeDynamoDbQuery query = queryFor(operations, "findByTournamentId", String.class);

			// Act
			query.execute(new Object[] { PARTITION_KEY });

			// Assert
			String keyCondition = operations.lastCapturedRequest.getKeyConditionExpression();
			assertAll(() -> assertNotNull(operations.lastCapturedRequest),
					() -> assertTrue(!keyCondition.contains("begins_with"),
							"did not expect a sort condition in: " + keyCondition),
					() -> assertEquals(1, operations.lastCapturedRequest.getExpressionAttributeValues().size()));
		}
	}
}
