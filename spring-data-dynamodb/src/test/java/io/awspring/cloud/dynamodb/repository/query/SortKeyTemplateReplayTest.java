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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;

public class SortKeyTemplateReplayTest {

	@Table(tableName = "orders")
	@SortKeyTemplate("MATCH#{year}#{round}")
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

	@Test
	void leadingPlaceholderSubsetReplaysAsABeginsWithKeyConditionWithoutThrowing() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		PartTreeDynamoDbQuery query = queryFor(operations, "findByTournamentIdAndYear", String.class, int.class);

		query.execute(new Object[] { "cust-1", 2024 });

		assertNotNull(operations.lastCapturedRequest);
		assertNull(operations.lastCapturedRequest.getIndexName());
		String keyCondition = operations.lastCapturedRequest.getKeyConditionExpression();
		assertTrue(keyCondition.contains("begins_with"), "expected begins_with in: " + keyCondition);
		Assertions.assertTrue(operations.lastCapturedRequest.getExpressionAttributeValues().containsValue("cust-1"));
		Assertions
				.assertTrue(operations.lastCapturedRequest.getExpressionAttributeValues().containsValue("MATCH#2024#"));
	}

	@Test
	void allPlaceholdersBoundReplaysAsAnExactEqKeyCondition() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		PartTreeDynamoDbQuery query = queryFor(operations, "findByTournamentIdAndYearAndRound", String.class, int.class,
				String.class);

		query.execute(new Object[] { "cust-1", 2024, "QUARTERFINAL" });

		assertNotNull(operations.lastCapturedRequest);
		String keyCondition = operations.lastCapturedRequest.getKeyConditionExpression();
		assertTrue(keyCondition.contains("="), "expected an equality clause in: " + keyCondition);
		assertTrue(keyCondition.contains("AND"), "expected partition AND sort clauses in: " + keyCondition);
		Assertions.assertTrue(
				operations.lastCapturedRequest.getExpressionAttributeValues().containsValue("MATCH#2024#QUARTERFINAL"));
	}

	@Test
	void beginsWithKeyConditionHasExactlyTheSyntaxRealDynamoDbAccepts() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		PartTreeDynamoDbQuery query = queryFor(operations, "findByTournamentIdAndYear", String.class, int.class);

		query.execute(new Object[] { "cust-1", 2024 });

		Assertions.assertEquals("#tk0 = :tk0 AND begins_with(#tk1, :tk1)",
				operations.lastCapturedRequest.getKeyConditionExpression());
	}

	@Test
	void fullyBoundEqKeyConditionHasExactlyTheSyntaxRealDynamoDbAccepts() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		PartTreeDynamoDbQuery query = queryFor(operations, "findByTournamentIdAndYearAndRound", String.class, int.class,
				String.class);

		query.execute(new Object[] { "cust-1", 2024, "QUARTERFINAL" });

		Assertions.assertEquals("#tk0 = :tk0 AND #tk1 = :tk1",
				operations.lastCapturedRequest.getKeyConditionExpression());
	}

	@Test
	void partitionKeyAloneReplaysWithNoSortConditionAtAll() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		PartTreeDynamoDbQuery query = queryFor(operations, "findByTournamentId", String.class);

		query.execute(new Object[] { "cust-1" });

		assertNotNull(operations.lastCapturedRequest);
		String keyCondition = operations.lastCapturedRequest.getKeyConditionExpression();
		assertFalseContainsSortColumn(keyCondition);
		Assertions.assertEquals(1, operations.lastCapturedRequest.getExpressionAttributeValues().size());
	}

	private static void assertFalseContainsSortColumn(String keyCondition) {
		assertTrue(!keyCondition.contains("begins_with"), "did not expect a sort condition in: " + keyCondition);
	}
}
