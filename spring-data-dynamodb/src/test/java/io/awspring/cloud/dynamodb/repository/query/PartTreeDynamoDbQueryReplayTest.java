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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.dynamodb.core.EntityQueryResult;
import io.awspring.cloud.dynamodb.core.EntityReadResult;
import io.awspring.cloud.dynamodb.core.EntityWriteResult;
import io.awspring.cloud.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.AllowScan;
import io.awspring.cloud.dynamodb.request.DynamoDbConditionRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbConditionRequestInterface;
import io.awspring.cloud.dynamodb.request.DynamoDbPageRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbQueryRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbQueryRequestInterface;
import io.awspring.cloud.dynamodb.request.DynamoDbScanRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbScanRequestInterface;
import io.awspring.cloud.dynamodb.request.DynamoDbUpdateExpressionRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbUpdateExpressionRequestInterface;
import io.awspring.cloud.dynamodb.request.IndexQueryBuilder;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;

public class PartTreeDynamoDbQueryReplayTest {

	interface MatchRepository extends org.springframework.data.repository.Repository<Match, String> {
		List<Match> findByTournamentId(String tournamentId);

		List<Match> findByTournamentIdAndRound(String tournamentId, String round);

		List<Match> findByRound(String round);

		Match findFirstByTournamentId(String tournamentId);

		List<Match> findTop2ByTournamentId(String tournamentId);

		@AllowScan
		List<Match> findAllowedByRound(String round);

		org.springframework.data.domain.Window<Match> findWindowByTournamentId(String tournamentId,
				org.springframework.data.domain.ScrollPosition scrollPosition,
				org.springframework.data.domain.Limit limit);

		org.springframework.data.domain.Slice<Match> findSliceByTournamentId(String tournamentId,
				org.springframework.data.domain.Pageable pageable);
	}

	@Table(tableName = "orders")
	static class Match {
		@PartitionKey
		String tournamentId;
		@SortKey
		String matchId;
		String round;
	}

	static final class CapturingOperations implements DynamoDbOperations {

		private final DynamoDbConverter converter;
		@Nullable
		DynamoDbQueryRequest lastCapturedRequest;
		@Nullable
		DynamoDbScanRequest lastCapturedScanRequest;
		@Nullable
		DynamoDbPageRequest lastCapturedPageRequest;

		@Nullable
		EntityQueryResult<List<Object>> scriptedQueryResult;

		@Nullable
		String lastStatement;
		@Nullable
		String lastStatementNextToken;
		@Nullable
		List<Object> lastStatementValues;
		@Nullable
		Boolean lastStatementConsistentRead;

		@Nullable
		EntityReadResult<List<Object>> scriptedReadResult;

		CapturingOperations(DynamoDbConverter converter) {
			this.converter = converter;
		}

		@Override
		public DynamoDbConverter getConverter() {
			return converter;
		}

		@Override
		public <T> IndexQueryBuilder<T> query(Class<T> entityClass, String indexName) {
			var entity = converter.getMappingContext().getRequiredPersistentEntity(entityClass);
			var keySchema = entity.getKeySchema();
			return new IndexQueryBuilder<>(entityClass, indexName, keySchema,
					(IndexQueryBuilder.QueryExecutor<T>) (clazz, request, pageRequest) -> {
						this.lastCapturedRequest = request;
						this.lastCapturedPageRequest = pageRequest;
						return scriptedResultOrEmpty();
					});
		}

		@Override
		public <T> EntityQueryResult<List<T>> query(Class<T> entityClass, DynamoDbQueryRequest queryRequest,
				DynamoDbPageRequest dynamoDBPageRequest) {
			this.lastCapturedRequest = queryRequest;
			this.lastCapturedPageRequest = dynamoDBPageRequest;
			return scriptedResultOrEmpty();
		}

		@SuppressWarnings("unchecked")
		private <T> EntityQueryResult<List<T>> scriptedResultOrEmpty() {
			return scriptedQueryResult != null ? (EntityQueryResult<List<T>>) (EntityQueryResult<?>) scriptedQueryResult
					: EntityQueryResultAccess.of(List.of());
		}

		@Override
		public <T> EntityQueryResult<List<T>> query(Class<T> entityClass, DynamoDbQueryRequestInterface builderFunction,
				DynamoDbPageRequest dynamoDBPageRequest) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> EntityQueryResult<List<T>> scan(Class<T> entityClass, DynamoDbScanRequest scanRequest) {
			this.lastCapturedScanRequest = scanRequest;
			return EntityQueryResultAccess.of(List.of());
		}

		@Override
		public <T> EntityQueryResult<List<T>> scan(Class<T> entityClass, DynamoDbScanRequestInterface builderFunction) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> long count(Class<T> entityClass) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> long count(Class<T> entityClass, DynamoDbScanRequest scanRequest) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> boolean exists(Class<T> entityClass, DynamoDbScanRequest scanRequest) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> boolean existsById(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public EntityQueryResult<List<Object>> queryPolymorphic(String tableName, DynamoDbQueryRequest queryRequest,
				@Nullable DynamoDbPageRequest dynamoDBPageRequest) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public EntityQueryResult<List<Object>> scanPolymorphic(String tableName, DynamoDbScanRequest scanRequest) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> EntityWriteResult<T> save(T entity) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> EntityWriteResult<T> save(T entity, DynamoDbConditionRequest dynamoDBConditionRequest) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> EntityWriteResult<T> save(T entity, DynamoDbConditionRequestInterface builderFunction) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> EntityWriteResult<T> insert(T entity) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> Iterable<T> saveAll(Iterable<T> entities) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> List<T> findAll(Class<T> entityClass) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public void delete(Object entity) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey,
				DynamoDbConditionRequest dynamoDBConditionRequest) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> void delete(Class<T> entityClass, Object primaryKey, @Nullable Object sortKey,
				DynamoDbConditionRequestInterface builderFunction) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass,
				List<Object> values) {
			return executeStatement(statement, nextToken, entityClass, values, Boolean.FALSE);
		}

		@Override
		public <T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken,
				Class<T> entityClass) {
			return executeStatement(statement, nextToken, entityClass, null, Boolean.FALSE);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> EntityReadResult<List<T>> executeStatement(String statement, String nextToken, Class<T> entityClass,
				List<Object> values, Boolean consistentRead) {
			this.lastStatement = statement;
			this.lastStatementNextToken = nextToken;
			this.lastStatementValues = values;
			this.lastStatementConsistentRead = consistentRead;
			return scriptedReadResult != null ? (EntityReadResult<List<T>>) (EntityReadResult<?>) scriptedReadResult
					: EntityReadResultAccess.of(List.of());
		}

		@Override
		public <T> T findById(Object id, Class<T> entityClass) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> T findById(Object id, Class<T> entityClass, Boolean consistentRead) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> T findById(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> T findById(Object partitionKey, @Nullable Object sortKey, Class<T> entityClass,
				Boolean consistentRead) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> EntityWriteResult<T> update(T entity) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> EntityWriteResult<T> update(Object partitionKey, @Nullable Object sortKey,
				DynamoDbUpdateExpressionRequest dynamoDBUpdateExpressionRequest, Class<T> entityClass) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> EntityWriteResult<T> update(Object partitionKey, @Nullable Object sortKey,
				DynamoDbUpdateExpressionRequestInterface builderFunction, Class<T> entityClass) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public String getTableName(Class<?> entityClass) {
			return converter.getMappingContext().getRequiredPersistentEntity(entityClass).getTableName();
		}
	}

	static final class EntityQueryResultAccess {
		@SuppressWarnings("unchecked")
		static <T> EntityQueryResult<T> of(T entity) {
			try {
				Method of = EntityQueryResult.class.getDeclaredMethod("of", Object.class);
				of.setAccessible(true);
				return (EntityQueryResult<T>) of.invoke(null, entity);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}

		@SuppressWarnings("unchecked")
		static <T> EntityQueryResult<T> of(T entity, @Nullable Map<String, Object> lastEvaluatedKey) {
			try {
				Method of = EntityQueryResult.class.getDeclaredMethod("of", Object.class, Integer.class, Map.class);
				of.setAccessible(true);
				return (EntityQueryResult<T>) of.invoke(null, entity, null, lastEvaluatedKey);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	static final class EntityReadResultAccess {
		@SuppressWarnings("unchecked")
		static <T> EntityReadResult<T> of(T entity) {
			return of(entity, null);
		}

		@SuppressWarnings("unchecked")
		static <T> EntityReadResult<T> of(T entity, @Nullable String nextToken) {
			try {
				Method of = EntityReadResult.class.getDeclaredMethod("of", Object.class, String.class);
				of.setAccessible(true);
				return (EntityReadResult<T>) of.invoke(null, entity, nextToken);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	@Test
	void sliceReturnTypeIsRejectedAtQueryMethodConstruction() {
		org.springframework.dao.InvalidDataAccessApiUsageException ex = assertThrows(
				org.springframework.dao.InvalidDataAccessApiUsageException.class,
				() -> queryMethodFor("findSliceByTournamentId", String.class,
						org.springframework.data.domain.Pageable.class));
		assertTrue(ex.getMessage().contains("Slice"));
		assertTrue(ex.getMessage().contains("Window"));
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

	@Test
	void partitionOnlyQueryReplaysIntoAKeyConditionOnTheBaseTable() throws NoSuchMethodException {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		CapturingOperations operations = new CapturingOperations(converter);

		DynamoDbQueryMethod queryMethod = queryMethodFor("findByTournamentId", String.class);
		PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

		query.execute(new Object[] { "cust-1" });

		assertNotNull(operations.lastCapturedRequest);
		assertNull(operations.lastCapturedRequest.getIndexName());
		assertTrue(operations.lastCapturedRequest.getKeyConditionExpression().contains("="));
		assertTrue(operations.lastCapturedRequest.getExpressionAttributeValues().containsValue("cust-1"));
	}

	@Test
	void findFirstReturnsSingleResultWithoutThrowingWhenMultipleMatchAndAppliesLimitOne() throws NoSuchMethodException {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		CapturingOperations operations = new CapturingOperations(converter);

		Match first = new Match();
		first.tournamentId = "cust-1";
		first.matchId = "match-1";
		Match second = new Match();
		second.tournamentId = "cust-1";
		second.matchId = "match-2";
		operations.scriptedQueryResult = EntityQueryResultAccess.of(List.<Object> of(first, second));

		DynamoDbQueryMethod queryMethod = queryMethodFor("findFirstByTournamentId", String.class);
		PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

		Object result = query.execute(new Object[] { "cust-1" });

		assertNotNull(result);
		assertEquals(first, result);
		assertNotNull(operations.lastCapturedPageRequest);
		assertEquals(1, operations.lastCapturedPageRequest.getLimit());
	}

	@Test
	void findTopNAppliesTheDerivedLimitToTheQuery() throws NoSuchMethodException {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		CapturingOperations operations = new CapturingOperations(converter);

		DynamoDbQueryMethod queryMethod = queryMethodFor("findTop2ByTournamentId", String.class);
		PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

		query.execute(new Object[] { "cust-1" });

		assertNotNull(operations.lastCapturedPageRequest);
		assertEquals(2, operations.lastCapturedPageRequest.getLimit());
	}

	@Test
	void partitionPlusNonKeyEqualityReplaysWithAResolvableFilterExpression() throws NoSuchMethodException {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		CapturingOperations operations = new CapturingOperations(converter);

		DynamoDbQueryMethod queryMethod = queryMethodFor("findByTournamentIdAndRound", String.class, String.class);
		PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

		query.execute(new Object[] { "cust-1", "QUARTERFINAL" });

		DynamoDbQueryRequest request = operations.lastCapturedRequest;
		assertNotNull(request);
		assertNotNull(request.getFilterExpression());
		assertFalse(request.getFilterExpression().isEmpty());
		assertTrue(request.getExpressionAttributeValues().containsValue("QUARTERFINAL"));
		for (String token : request.getExpressionAttributeNames().keySet()) {
			assertTrue(request.getFilterExpression().contains(token)
					|| request.getKeyConditionExpression().contains(token));
		}
	}

	@Test
	void indexServableMethodConstructsWithoutAllowScan() throws NoSuchMethodException {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		CapturingOperations operations = new CapturingOperations(converter);

		DynamoDbQueryMethod queryMethod = queryMethodFor("findByTournamentId", String.class);

		assertNotNull(new PartTreeDynamoDbQuery(queryMethod, operations));
	}

	@Test
	void scanRequiringMethodWithoutAllowScanFailsAtConstructionNotAtFirstInvocation() throws NoSuchMethodException {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		CapturingOperations operations = new CapturingOperations(converter);

		DynamoDbQueryMethod queryMethod = queryMethodFor("findByRound", String.class);

		org.springframework.dao.InvalidDataAccessApiUsageException ex = assertThrows(
				org.springframework.dao.InvalidDataAccessApiUsageException.class,
				() -> new PartTreeDynamoDbQuery(queryMethod, operations));
		assertTrue(ex.getMessage().contains("AllowScan"));
		assertNull(operations.lastCapturedRequest);
	}

	@Test
	void scanRequiringMethodWithAllowScanConstructsAndExecutesAsAScan() throws NoSuchMethodException {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		CapturingOperations operations = new CapturingOperations(converter);

		DynamoDbQueryMethod queryMethod = queryMethodFor("findAllowedByRound", String.class);

		PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

		query.execute(new Object[] { "QUARTERFINAL" });

		assertNotNull(operations.lastCapturedScanRequest);
		assertNotNull(operations.lastCapturedScanRequest.getFilterExpression());
		assertTrue(operations.lastCapturedScanRequest.getExpressionAttributeValues().containsValue("QUARTERFINAL"));
	}

	@Test
	void windowWithMoreResultsCarriesAForwardScrollPositionAndHasNext() throws NoSuchMethodException {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		CapturingOperations operations = new CapturingOperations(converter);

		Match match = new Match();
		match.tournamentId = "cust-1";
		match.matchId = "match-1";
		Map<String, Object> nextKey = Map.of("tournamentId", "cust-1", "matchId", "match-1");
		operations.scriptedQueryResult = EntityQueryResultAccess.of(List.<Object> of(match), nextKey);

		DynamoDbQueryMethod queryMethod = queryMethodFor("findWindowByTournamentId", String.class, ScrollPosition.class,
				Limit.class);
		PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

		Object result = query.execute(new Object[] { "cust-1", ScrollPosition.keyset(), Limit.of(10) });

		assertNotNull(result);
		org.springframework.data.domain.Window<?> window = (org.springframework.data.domain.Window<?>) result;
		assertTrue(window.hasNext());
		assertEquals(1, window.size());
		org.springframework.data.domain.ScrollPosition nextPosition = window.positionAt(0);
		assertTrue(nextPosition instanceof org.springframework.data.domain.KeysetScrollPosition);
		assertEquals(nextKey, ((org.springframework.data.domain.KeysetScrollPosition) nextPosition).getKeys());
		assertNotNull(operations.lastCapturedPageRequest);
		assertEquals(10, operations.lastCapturedPageRequest.getLimit());
	}

	@Test
	void windowOnTheLastPageHasNoNextAndAnInitialScrollPosition() throws NoSuchMethodException {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		CapturingOperations operations = new CapturingOperations(converter);

		Match match = new Match();
		match.tournamentId = "cust-1";
		operations.scriptedQueryResult = EntityQueryResultAccess.of(List.<Object> of(match), null);

		DynamoDbQueryMethod queryMethod = queryMethodFor("findWindowByTournamentId", String.class, ScrollPosition.class,
				Limit.class);
		PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

		Window<?> window = (Window<?>) query
				.execute(new Object[] { "cust-1", ScrollPosition.keyset(), Limit.unlimited() });

		assertFalse(window.hasNext());
	}

	@Test
	void inboundKeysetScrollPositionBecomesTheExclusiveStartKeyOnTheNextRequest() throws NoSuchMethodException {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		CapturingOperations operations = new CapturingOperations(converter);
		operations.scriptedQueryResult = EntityQueryResultAccess.of(List.of(), null);

		DynamoDbQueryMethod queryMethod = queryMethodFor("findWindowByTournamentId", String.class, ScrollPosition.class,
				Limit.class);
		PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

		Map<String, Object> resumeFrom = Map.of("tournamentId", "cust-1", "matchId", "match-1");
		query.execute(new Object[] { "cust-1", ScrollPosition.forward(resumeFrom), Limit.unlimited() });

		assertNotNull(operations.lastCapturedPageRequest);
		assertEquals(resumeFrom, operations.lastCapturedPageRequest.getLastEvaluatedKey());
	}
}
