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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.DynamoDbRepositoryFactory;
import io.awspring.cloud.dynamodb.repository.Modifying;
import io.awspring.cloud.dynamodb.repository.Query;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.core.support.PropertiesBasedNamedQueries;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ValueExpressionDelegate;

public class D1PartiQlModifyingInterplayTest {

	@Table(tableName = "orders")
	static class Match {
		@PartitionKey
		String tournamentId;
		String round;
	}

	interface MatchRepository extends Repository<Match, String> {

		@Query(partiQl = "SELECT * FROM orders WHERE tournamentId = ?")
		List<Match> byTournament(@Param("tournamentId") String tournamentId);

		@Modifying
		@Query(updateExpression = "SET #s = :round")
		Match updateStatus(@Param("tournamentId") String tournamentId, @Param("round") String round);

		@Query(filterExpression = "#s = :round")
		List<Match> scanByStatus(@Param("round") String round);
	}

	private static final class ExposedFactory extends DynamoDbRepositoryFactory {
		ExposedFactory(DynamoDbOperations operations) {
			super(operations);
		}

		QueryLookupStrategy lookupStrategy() {
			return getQueryLookupStrategy(QueryLookupStrategy.Key.CREATE_IF_NOT_FOUND, ValueExpressionDelegate.create())
					.orElseThrow();
		}
	}

	private static PartTreeDynamoDbQueryReplayTest.CapturingOperations newOperations() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		return new PartTreeDynamoDbQueryReplayTest.CapturingOperations(converter);
	}

	private static DynamoDbQueryMethod queryMethodFor(String name, Class<?>... paramTypes)
			throws NoSuchMethodException {
		Method method = MatchRepository.class.getMethod(name, paramTypes);
		RepositoryMetadata metadata = new DefaultRepositoryMetadata(MatchRepository.class);
		ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		return new DynamoDbQueryMethod(method, metadata, projectionFactory, mappingContext);
	}

	private static RepositoryQuery resolveVia(PartTreeDynamoDbQueryReplayTest.CapturingOperations operations,
			String name, Class<?>... paramTypes) throws NoSuchMethodException {
		Method method = MatchRepository.class.getMethod(name, paramTypes);
		RepositoryMetadata metadata = new DefaultRepositoryMetadata(MatchRepository.class);
		ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
		NamedQueries namedQueries = new PropertiesBasedNamedQueries(new Properties());
		return new ExposedFactory(operations).lookupStrategy().resolveQuery(method, metadata, projectionFactory,
				namedQueries);
	}

	@Test
	void partiQlMethodIsAnnotatedQueryAndSoTakesTheStringBasedBranch() throws NoSuchMethodException {
		DynamoDbQueryMethod queryMethod = queryMethodFor("byTournament", String.class);
		assertTrue(queryMethod.isPartiQlQuery(), "PartiQL method must classify as PartiQL");
		assertTrue(queryMethod.hasAnnotatedQuery(),
				"A PartiQL method carries @Query, so the factory routes it to StringBasedDynamoDbQuery");
	}

	@Test
	void modifyingMethodIsAnnotatedQueryAndSoTakesTheStringBasedBranch() throws NoSuchMethodException {
		DynamoDbQueryMethod queryMethod = queryMethodFor("updateStatus", String.class, String.class);
		assertTrue(queryMethod.isModifyingQuery(), "@Modifying method must classify as modifying");
		assertTrue(queryMethod.hasAnnotatedQuery(),
				"A @Modifying update carries @Query(updateExpression=...), so the factory routes it to StringBasedDynamoDbQuery");
	}

	@Test
	void factoryDispatchesPartiQlMethodToStringBasedNeverPartTree() throws NoSuchMethodException {
		RepositoryQuery query = resolveVia(newOperations(), "byTournament", String.class);
		assertInstanceOf(StringBasedDynamoDbQuery.class, query,
				"PartiQL method must be served by StringBasedDynamoDbQuery");
		assertFalse(query instanceof PartTreeDynamoDbQuery,
				"PartiQL method must NEVER reach PartTreeDynamoDbQuery (the only class with the eager D1 check)");
	}

	@Test
	void factoryDispatchesModifyingMethodToStringBasedNeverPartTree() throws NoSuchMethodException {
		RepositoryQuery query = resolveVia(newOperations(), "updateStatus", String.class, String.class);
		assertInstanceOf(StringBasedDynamoDbQuery.class, query,
				"@Modifying update must be served by StringBasedDynamoDbQuery");
		assertFalse(query instanceof PartTreeDynamoDbQuery,
				"@Modifying update must NEVER reach PartTreeDynamoDbQuery (the only class with the eager D1 check)");
	}

	@Test
	void stringBasedConstructionDoesNoEagerScanCheckEvenForAScanShapedQuery() throws NoSuchMethodException {
		RepositoryQuery query = resolveVia(newOperations(), "scanByStatus", String.class);
		assertNotNull(query, "StringBasedDynamoDbQuery constructs a scan-shaped @Query without any eager check");
		assertInstanceOf(StringBasedDynamoDbQuery.class, query);
	}
}
