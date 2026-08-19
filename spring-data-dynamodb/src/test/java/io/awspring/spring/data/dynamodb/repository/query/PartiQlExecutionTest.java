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

import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.repository.Query;
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
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ValueExpressionDelegate;

@DisplayName("PartiQL execution")
class PartiQlExecutionTest {

	private static final String TABLE_NAME = "orders";
	private static final String STATEMENT_SINGLE = "SELECT * FROM orders WHERE tournamentId = ?";
	private static final String STATEMENT_DOUBLE = "SELECT * FROM orders WHERE tournamentId = ? AND round = ?";
	private static final String PK_CUST_1 = "cust-1";
	private static final String PK_CUST_7 = "cust-7";
	private static final String ROUND_QUARTERFINAL = "QUARTERFINAL";

	@Table(tableName = TABLE_NAME)
	static class Match {
		@PartitionKey
		String tournamentId;
		@SortKey
		String matchId;
		String round;
	}

	interface MatchRepository extends Repository<Match, String> {

		@Query(partiQl = STATEMENT_SINGLE)
		List<Match> byTournament(@Param("tournamentId") String tournamentId);

		@Query(partiQl = STATEMENT_DOUBLE)
		List<Match> byTournamentAndRound(@Param("tournamentId") String tournamentId, @Param("round") String round);

		@Query(partiQl = STATEMENT_SINGLE, consistentRead = true)
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

	@Nested
	@DisplayName("Positional parameter binding")
	class ParameterBindingTests {

		@Test
		@DisplayName("single positional parameter binds in order")
		void singlePositionalParameterBindsInOrder() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "byTournament", String.class);

			// Act
			query.execute(new Object[] { PK_CUST_1 });

			// Assert
			assertAll(() -> assertEquals(STATEMENT_SINGLE, operations.lastStatement),
					() -> assertNotNull(operations.lastStatementValues),
					() -> assertEquals(List.of(PK_CUST_1), operations.lastStatementValues));
		}

		@Test
		@DisplayName("two positional parameters bind in declaration order")
		void twoPositionalParametersBindInDeclarationOrder() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			StringBasedDynamoDbQuery query = queryFor(operations, "byTournamentAndRound", String.class, String.class);

			// Act
			query.execute(new Object[] { PK_CUST_7, ROUND_QUARTERFINAL });

			// Assert
			assertAll(() -> assertEquals(STATEMENT_DOUBLE, operations.lastStatement),
					() -> assertNotNull(operations.lastStatementValues),
					() -> assertEquals(List.of(PK_CUST_7, ROUND_QUARTERFINAL), operations.lastStatementValues));
		}
	}

	@Nested
	@DisplayName("Consistent read and result handling")
	class ConsistentReadAndResultTests {

		@Test
		@DisplayName("consistentRead flows through to the statement execution")
		void consistentReadFlowsThrough() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();

			// Act & Assert - consistent
			StringBasedDynamoDbQuery consistent = queryFor(operations, "byCustomerConsistent", String.class);
			consistent.execute(new Object[] { PK_CUST_1 });
			assertEquals(Boolean.TRUE, operations.lastStatementConsistentRead);

			// Act & Assert - not consistent
			StringBasedDynamoDbQuery notConsistent = queryFor(operations, "byTournament", String.class);
			notConsistent.execute(new Object[] { PK_CUST_1 });
			assertEquals(Boolean.FALSE, operations.lastStatementConsistentRead);
		}

		@Test
		@DisplayName("collection result is returned as a list")
		void collectionResultIsReturnedAsAList() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
			Match match = new Match();
			match.tournamentId = PK_CUST_1;
			operations.scriptedReadResult = PartTreeDynamoDbQueryReplayTest.EntityReadResultAccess
					.of(List.<Object> of(match), null);

			StringBasedDynamoDbQuery query = queryFor(operations, "byTournament", String.class);

			// Act
			Object result = query.execute(new Object[] { PK_CUST_1 });

			// Assert
			assertAll(() -> assertNotNull(result), () -> assertFalse(((List<?>) result).isEmpty()),
					() -> assertEquals(1, ((List<?>) result).size()));
		}
	}
}
