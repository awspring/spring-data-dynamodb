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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.Query;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ValueExpressionDelegate;

public class PartiQlExecutionTest {

	@Table(tableName = "orders")
	static class Match {
		@PartitionKey
		String tournamentId;
		@SortKey
		String matchId;
		String round;
	}

	interface MatchRepository extends Repository<Match, String> {

		@Query(partiQl = "SELECT * FROM orders WHERE tournamentId = ?")
		List<Match> byTournament(@Param("tournamentId") String tournamentId);

		@Query(partiQl = "SELECT * FROM orders WHERE tournamentId = ? AND round = ?")
		List<Match> byTournamentAndRound(@Param("tournamentId") String tournamentId, @Param("round") String round);

		@Query(partiQl = "SELECT * FROM orders WHERE tournamentId = ?", consistentRead = true)
		List<Match> byCustomerConsistent(@Param("tournamentId") String tournamentId);
	}

	private PartTreeDynamoDbQueryReplayTest.CapturingOperations operations() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		return new PartTreeDynamoDbQueryReplayTest.CapturingOperations(converter);
	}

	private StringBasedDynamoDbQuery queryFor(PartTreeDynamoDbQueryReplayTest.CapturingOperations operations,
			String name, Class<?>... paramTypes) throws NoSuchMethodException {
		Method method = MatchRepository.class.getMethod(name, paramTypes);
		RepositoryMetadata metadata = new DefaultRepositoryMetadata(MatchRepository.class);
		ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		DynamoDbQueryMethod queryMethod = new DynamoDbQueryMethod(method, metadata, projectionFactory, mappingContext);
		return new StringBasedDynamoDbQuery(queryMethod, operations, ValueExpressionDelegate.create());
	}

	@Test
	void singlePositionalParameterBindsInOrder() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "byTournament", String.class);

		query.execute(new Object[] { "cust-1" });

		assertEquals("SELECT * FROM orders WHERE tournamentId = ?", operations.lastStatement);
		assertNotNull(operations.lastStatementValues);
		assertEquals(List.of("cust-1"), operations.lastStatementValues);
	}

	@Test
	void twoPositionalParametersBindInDeclarationOrder() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "byTournamentAndRound", String.class, String.class);

		query.execute(new Object[] { "cust-7", "QUARTERFINAL" });

		assertEquals("SELECT * FROM orders WHERE tournamentId = ? AND round = ?", operations.lastStatement);
		assertNotNull(operations.lastStatementValues);
		assertEquals(List.of("cust-7", "QUARTERFINAL"), operations.lastStatementValues);
	}

	@Test
	void consistentReadFlowsThrough() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();

		StringBasedDynamoDbQuery consistent = queryFor(operations, "byCustomerConsistent", String.class);
		consistent.execute(new Object[] { "cust-1" });
		assertEquals(Boolean.TRUE, operations.lastStatementConsistentRead);

		StringBasedDynamoDbQuery notConsistent = queryFor(operations, "byTournament", String.class);
		notConsistent.execute(new Object[] { "cust-1" });
		assertEquals(Boolean.FALSE, operations.lastStatementConsistentRead);
	}

	@Test
	void collectionResultIsReturnedAsAList() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		Match match = new Match();
		match.tournamentId = "cust-1";
		operations.scriptedReadResult = PartTreeDynamoDbQueryReplayTest.EntityReadResultAccess
				.of(List.<Object> of(match), null);

		StringBasedDynamoDbQuery query = queryFor(operations, "byTournament", String.class);
		Object result = query.execute(new Object[] { "cust-1" });

		assertNotNull(result);
		assertFalse(((List<?>) result).isEmpty());
		assertEquals(1, ((List<?>) result).size());
	}
}
