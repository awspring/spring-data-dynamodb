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
import io.awspring.cloud.dynamodb.request.DynamoDbPageRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbQueryRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbScanRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbUpdateExpressionRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbUpdateExpressionRequestInterface;
import io.awspring.cloud.dynamodb.request.IndexQueryBuilder;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;

@DisplayName("PartTreeDynamoDbQuery — replay-based verification of query derivation")
class PartTreeDynamoDbQueryReplayTest {

	private static final String PARTITION_KEY_VALUE = "cust-1";
	private static final String MATCH_ID_1 = "match-1";
	private static final String MATCH_ID_2 = "match-2";
	private static final String ROUND_QUARTERFINAL = "QUARTERFINAL";
	private static final String TABLE_NAME = "orders";

	interface MatchRepository extends Repository<Match, String> {
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

	@Table(tableName = TABLE_NAME)
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

		final Deque<EntityQueryResult<List<Object>>> scriptedQueryPages = new ArrayDeque<>();

		int queryInvocations;

		long scriptedCount;
		boolean countedViaQuery;

		@Nullable
		Object scriptedUpdatedEntity;
		@Nullable
		Object lastUpdatePartitionKey;
		@Nullable
		Object lastUpdateSortKey;
		@Nullable
		DynamoDbUpdateExpressionRequest lastCapturedUpdateRequest;

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
			this.queryInvocations++;
			return scriptedResultOrEmpty();
		}

		@SuppressWarnings("unchecked")
		private <T> EntityQueryResult<List<T>> scriptedResultOrEmpty() {
			if (!scriptedQueryPages.isEmpty()) {
				return (EntityQueryResult<List<T>>) (EntityQueryResult<?>) scriptedQueryPages.poll();
			}
			return scriptedQueryResult != null ? (EntityQueryResult<List<T>>) (EntityQueryResult<?>) scriptedQueryResult
					: EntityQueryResultAccess.of(List.of());
		}

		@Override
		public <T> EntityQueryResult<List<T>> scan(Class<T> entityClass, DynamoDbScanRequest scanRequest) {
			this.lastCapturedScanRequest = scanRequest;
			return EntityQueryResultAccess.of(List.of());
		}

		@Override
		public <T> long count(Class<T> entityClass) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> long count(Class<T> entityClass, DynamoDbScanRequest scanRequest) {
			this.lastCapturedScanRequest = scanRequest;
			return scriptedCount;
		}

		@Override
		public <T> boolean exists(Class<T> entityClass, DynamoDbScanRequest scanRequest) {
			this.lastCapturedScanRequest = scanRequest;
			return scriptedCount > 0L;
		}

		@Override
		public <T> long count(Class<T> entityClass, DynamoDbQueryRequest queryRequest) {
			this.lastCapturedRequest = queryRequest;
			this.countedViaQuery = true;
			return scriptedCount;
		}

		@Override
		public <T> boolean exists(Class<T> entityClass, DynamoDbQueryRequest queryRequest) {
			this.lastCapturedRequest = queryRequest;
			this.countedViaQuery = true;
			return scriptedCount > 0L;
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
		public <T> EntityWriteResult<T> insert(T entity) {
			throw new UnsupportedOperationException("not exercised by this test");
		}

		@Override
		public <T> Iterable<T> saveAll(Iterable<? extends T> entities) {
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
			this.lastUpdatePartitionKey = partitionKey;
			this.lastUpdateSortKey = sortKey;
			this.lastCapturedUpdateRequest = dynamoDBUpdateExpressionRequest;
			return EntityWriteResultAccess.of(entityClass.cast(scriptedUpdatedEntity));
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

		@Override
		@Nullable
		public <A> EntityQueryResult<A> queryAggregate(Class<A> aggregateClass, DynamoDbQueryRequest dynamoDbRequest,
				DynamoDbPageRequest dynamoDBPageRequest) {
			throw new UnsupportedOperationException("not exercised by this test");
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

	static final class EntityWriteResultAccess {
		@SuppressWarnings("unchecked")
		static <T> EntityWriteResult<T> of(@Nullable T entity) {
			try {
				Method of = EntityWriteResult.class.getDeclaredMethod("of", Object.class);
				of.setAccessible(true);
				return (EntityWriteResult<T>) of.invoke(null, entity);
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

	// ─── Shared fixture setup ───────────────────────────────────────────────────

	private CapturingOperations newCapturingOperations() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		return new CapturingOperations(converter);
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

	// ─── Tests ──────────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("Return type validation")
	class ReturnTypeValidation {

		@Test
		@DisplayName("Slice return type is rejected at query method construction time")
		void sliceReturnTypeIsRejectedAtQueryMethodConstruction() {
			org.springframework.dao.InvalidDataAccessApiUsageException ex = assertThrows(
					org.springframework.dao.InvalidDataAccessApiUsageException.class,
					() -> queryMethodFor("findSliceByTournamentId", String.class,
							org.springframework.data.domain.Pageable.class));

			assertAll(() -> assertTrue(ex.getMessage().contains("Slice")),
					() -> assertTrue(ex.getMessage().contains("Window")));
		}
	}

	@Nested
	@DisplayName("Partition-key queries")
	class PartitionKeyQueries {

		@Test
		@DisplayName("Partition-only query replays into a key condition on the base table")
		void partitionOnlyQueryReplaysIntoAKeyConditionOnTheBaseTable() throws NoSuchMethodException {
			CapturingOperations operations = newCapturingOperations();
			DynamoDbQueryMethod queryMethod = queryMethodFor("findByTournamentId", String.class);
			PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

			query.execute(new Object[] { PARTITION_KEY_VALUE });

			assertAll(() -> assertNotNull(operations.lastCapturedRequest),
					() -> assertNull(operations.lastCapturedRequest.getIndexName()),
					() -> assertTrue(operations.lastCapturedRequest.getKeyConditionExpression().contains("=")),
					() -> assertTrue(operations.lastCapturedRequest.getExpressionAttributeValues()
							.containsValue(PARTITION_KEY_VALUE)));
		}

		@Test
		@DisplayName("Partition plus non-key equality replays with a resolvable filter expression")
		void partitionPlusNonKeyEqualityReplaysWithAResolvableFilterExpression() throws NoSuchMethodException {
			CapturingOperations operations = newCapturingOperations();
			DynamoDbQueryMethod queryMethod = queryMethodFor("findByTournamentIdAndRound", String.class, String.class);
			PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

			query.execute(new Object[] { PARTITION_KEY_VALUE, ROUND_QUARTERFINAL });

			DynamoDbQueryRequest request = operations.lastCapturedRequest;
			assertAll(() -> assertNotNull(request), () -> assertNotNull(request.getFilterExpression()),
					() -> assertFalse(request.getFilterExpression().isEmpty()),
					() -> assertTrue(request.getExpressionAttributeValues().containsValue(ROUND_QUARTERFINAL)), () -> {
						for (String token : request.getExpressionAttributeNames().keySet()) {
							assertTrue(request.getFilterExpression().contains(token)
									|| request.getKeyConditionExpression().contains(token));
						}
					});
		}
	}

	@Nested
	@DisplayName("Limiting queries (findFirst / findTopN)")
	class LimitingQueries {

		@Test
		@DisplayName("findFirst returns a single result and applies limit=1")
		void findFirstReturnsSingleResultWithoutThrowingWhenMultipleMatchAndAppliesLimitOne()
				throws NoSuchMethodException {
			CapturingOperations operations = newCapturingOperations();

			Match first = new Match();
			first.tournamentId = PARTITION_KEY_VALUE;
			first.matchId = MATCH_ID_1;
			Match second = new Match();
			second.tournamentId = PARTITION_KEY_VALUE;
			second.matchId = MATCH_ID_2;
			operations.scriptedQueryResult = EntityQueryResultAccess.of(List.<Object> of(first, second));

			DynamoDbQueryMethod queryMethod = queryMethodFor("findFirstByTournamentId", String.class);
			PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

			Object result = query.execute(new Object[] { PARTITION_KEY_VALUE });

			assertAll(() -> assertNotNull(result), () -> assertEquals(first, result),
					() -> assertNotNull(operations.lastCapturedPageRequest),
					() -> assertEquals(1, operations.lastCapturedPageRequest.getLimit()));
		}

		@Test
		@DisplayName("findTopN applies the derived limit to the query")
		void findTopNAppliesTheDerivedLimitToTheQuery() throws NoSuchMethodException {
			CapturingOperations operations = newCapturingOperations();
			DynamoDbQueryMethod queryMethod = queryMethodFor("findTop2ByTournamentId", String.class);
			PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

			query.execute(new Object[] { PARTITION_KEY_VALUE });

			assertAll(() -> assertNotNull(operations.lastCapturedPageRequest),
					() -> assertEquals(2, operations.lastCapturedPageRequest.getLimit()));
		}
	}

	@Nested
	@DisplayName("Scan behaviour and @AllowScan gating")
	class ScanBehaviour {

		@Test
		@DisplayName("Index-servable method constructs without @AllowScan")
		void indexServableMethodConstructsWithoutAllowScan() throws NoSuchMethodException {
			CapturingOperations operations = newCapturingOperations();
			DynamoDbQueryMethod queryMethod = queryMethodFor("findByTournamentId", String.class);

			assertNotNull(new PartTreeDynamoDbQuery(queryMethod, operations));
		}

		@Test
		@DisplayName("Scan-requiring method without @AllowScan fails at construction, not at invocation")
		void scanRequiringMethodWithoutAllowScanFailsAtConstructionNotAtFirstInvocation() throws NoSuchMethodException {
			CapturingOperations operations = newCapturingOperations();
			DynamoDbQueryMethod queryMethod = queryMethodFor("findByRound", String.class);

			org.springframework.dao.InvalidDataAccessApiUsageException ex = assertThrows(
					org.springframework.dao.InvalidDataAccessApiUsageException.class,
					() -> new PartTreeDynamoDbQuery(queryMethod, operations));

			assertAll(() -> assertTrue(ex.getMessage().contains("AllowScan")),
					() -> assertNull(operations.lastCapturedRequest));
		}

		@Test
		@DisplayName("Scan-requiring method with @AllowScan constructs and executes as a scan")
		void scanRequiringMethodWithAllowScanConstructsAndExecutesAsAScan() throws NoSuchMethodException {
			CapturingOperations operations = newCapturingOperations();
			DynamoDbQueryMethod queryMethod = queryMethodFor("findAllowedByRound", String.class);
			PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

			query.execute(new Object[] { ROUND_QUARTERFINAL });

			assertAll(() -> assertNotNull(operations.lastCapturedScanRequest),
					() -> assertNotNull(operations.lastCapturedScanRequest.getFilterExpression()),
					() -> assertTrue(operations.lastCapturedScanRequest.getExpressionAttributeValues()
							.containsValue(ROUND_QUARTERFINAL)));
		}
	}

	@Nested
	@DisplayName("Window (keyset scroll) pagination")
	class WindowPagination {

		@Test
		@DisplayName("Window with more results carries a forward scroll position and hasNext=true")
		void windowWithMoreResultsCarriesAForwardScrollPositionAndHasNext() throws NoSuchMethodException {
			CapturingOperations operations = newCapturingOperations();

			Match match = new Match();
			match.tournamentId = PARTITION_KEY_VALUE;
			match.matchId = MATCH_ID_1;
			Map<String, Object> nextKey = Map.of("tournamentId", PARTITION_KEY_VALUE, "matchId", MATCH_ID_1);
			operations.scriptedQueryResult = EntityQueryResultAccess.of(List.<Object> of(match), nextKey);

			DynamoDbQueryMethod queryMethod = queryMethodFor("findWindowByTournamentId", String.class,
					ScrollPosition.class, Limit.class);
			PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

			Object result = query.execute(new Object[] { PARTITION_KEY_VALUE, ScrollPosition.keyset(), Limit.of(10) });

			assertNotNull(result);
			Window<?> window = (Window<?>) result;
			assertAll(() -> assertTrue(window.hasNext()), () -> assertEquals(1, window.size()), () -> {
				ScrollPosition nextPosition = window.positionAt(0);
				assertTrue(nextPosition instanceof KeysetScrollPosition);
				assertEquals(nextKey, ((KeysetScrollPosition) nextPosition).getKeys());
			}, () -> assertNotNull(operations.lastCapturedPageRequest),
					() -> assertEquals(10, operations.lastCapturedPageRequest.getLimit()));
		}

		@Test
		@DisplayName("Window on the last page has no next and an initial scroll position")
		void windowOnTheLastPageHasNoNextAndAnInitialScrollPosition() throws NoSuchMethodException {
			CapturingOperations operations = newCapturingOperations();

			Match match = new Match();
			match.tournamentId = PARTITION_KEY_VALUE;
			operations.scriptedQueryResult = EntityQueryResultAccess.of(List.<Object> of(match), null);

			DynamoDbQueryMethod queryMethod = queryMethodFor("findWindowByTournamentId", String.class,
					ScrollPosition.class, Limit.class);
			PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

			Window<?> window = (Window<?>) query
					.execute(new Object[] { PARTITION_KEY_VALUE, ScrollPosition.keyset(), Limit.unlimited() });

			assertFalse(window.hasNext());
		}

		@Test
		@DisplayName("Inbound keyset scroll position becomes the exclusive start key on the next request")
		void inboundKeysetScrollPositionBecomesTheExclusiveStartKeyOnTheNextRequest() throws NoSuchMethodException {
			CapturingOperations operations = newCapturingOperations();
			operations.scriptedQueryResult = EntityQueryResultAccess.of(List.of(), null);

			DynamoDbQueryMethod queryMethod = queryMethodFor("findWindowByTournamentId", String.class,
					ScrollPosition.class, Limit.class);
			PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

			Map<String, Object> resumeFrom = Map.of("tournamentId", PARTITION_KEY_VALUE, "matchId", MATCH_ID_1);

			query.execute(new Object[] { PARTITION_KEY_VALUE, ScrollPosition.forward(resumeFrom), Limit.unlimited() });

			assertAll(() -> assertNotNull(operations.lastCapturedPageRequest),
					() -> assertEquals(resumeFrom, operations.lastCapturedPageRequest.getLastEvaluatedKey()));
		}

		@Test
		@DisplayName("an empty page carrying a cursor is drained until a non-empty page, keeping the window usable")
		void anEmptyPageCarryingACursorIsDrainedUntilANonEmptyPage() throws NoSuchMethodException {
			CapturingOperations operations = newCapturingOperations();

			Map<String, Object> firstPageCursor = Map.of("tournamentId", PARTITION_KEY_VALUE, "matchId", MATCH_ID_1);
			Map<String, Object> secondPageCursor = Map.of("tournamentId", PARTITION_KEY_VALUE, "matchId", MATCH_ID_2);
			Match match = new Match();
			match.tournamentId = PARTITION_KEY_VALUE;
			match.matchId = MATCH_ID_2;
			operations.scriptedQueryPages.add(EntityQueryResultAccess.of(List.<Object> of(), firstPageCursor));
			operations.scriptedQueryPages.add(EntityQueryResultAccess.of(List.<Object> of(match), secondPageCursor));

			DynamoDbQueryMethod queryMethod = queryMethodFor("findWindowByTournamentId", String.class,
					ScrollPosition.class, Limit.class);
			PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);

			Window<?> window = (Window<?>) query
					.execute(new Object[] { PARTITION_KEY_VALUE, ScrollPosition.keyset(), Limit.of(10) });

			assertAll(
					() -> assertEquals(2, operations.queryInvocations,
							"the empty first page must be drained, not surfaced as an unusable empty window"),
					() -> assertEquals(1, window.size(), "the first non-empty page's content is returned"),
					() -> assertTrue(window.hasNext(), "the resumed page still had a cursor, so there is a next page"),
					() -> {
						ScrollPosition next = window.positionAt(window.size() - 1);
						assertTrue(next instanceof KeysetScrollPosition);
						assertEquals(secondPageCursor, ((KeysetScrollPosition) next).getKeys());
					}, () -> assertEquals(firstPageCursor, operations.lastCapturedPageRequest.getLastEvaluatedKey(),
							"the drained page's cursor becomes the exclusive start key of the resumed request"));
		}
	}
}
