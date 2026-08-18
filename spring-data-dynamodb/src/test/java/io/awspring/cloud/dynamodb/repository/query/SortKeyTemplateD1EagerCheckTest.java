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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.AllowScan;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;

@DisplayName("SortKeyTemplate D1 eager scan check")
class SortKeyTemplateD1EagerCheckTest {

	private static final String TABLE_NAME = "orders";
	private static final String SORT_KEY_TEMPLATE = "MATCH#{year}#{round}";
	private static final String ROUND_VALUE = "QUARTERFINAL";

	@Table(tableName = TABLE_NAME)
	@SortKeyTemplate(SORT_KEY_TEMPLATE)
	static class Match {
		@PartitionKey
		String tournamentId;
		int year;
		String round;
	}

	interface MatchTemplateRepository extends Repository<Match, String> {

		List<Match> findByTournamentId(String tournamentId);

		List<Match> findByTournamentIdAndYear(String tournamentId, int year);

		List<Match> findByTournamentIdAndYearAndRound(String tournamentId, int year, String round);

		List<Match> findByRound(String round);

		@AllowScan
		List<Match> findAllowedByRound(String round);
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
		Method method = MatchTemplateRepository.class.getMethod(name, paramTypes);
		RepositoryMetadata metadata = new DefaultRepositoryMetadata(MatchTemplateRepository.class);
		ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Match.class);
		return new DynamoDbQueryMethod(method, metadata, projectionFactory, mappingContext);
	}

	@Nested
	@DisplayName("Methods that construct without @AllowScan (index-servable)")
	class IndexServableTests {

		@Test
		@DisplayName("partition key alone constructs without @AllowScan")
		void partitionKeyAloneConstructsWithoutAllowScan() throws NoSuchMethodException {
			DynamoDbQueryMethod queryMethod = queryMethodFor("findByTournamentId", String.class);

			assertNotNull(new PartTreeDynamoDbQuery(queryMethod, newOperations()));
		}

		@Test
		@DisplayName("partition + leading template placeholder constructs without @AllowScan")
		void partitionPlusLeadingTemplatePlaceholderConstructsWithoutAllowScan() throws NoSuchMethodException {
			DynamoDbQueryMethod queryMethod = queryMethodFor("findByTournamentIdAndYear", String.class, int.class);

			assertNotNull(new PartTreeDynamoDbQuery(queryMethod, newOperations()));
		}

		@Test
		@DisplayName("all template placeholders bound constructs without @AllowScan")
		void allTemplatePlaceholdersBoundConstructsWithoutAllowScan() throws NoSuchMethodException {
			DynamoDbQueryMethod queryMethod = queryMethodFor("findByTournamentIdAndYearAndRound", String.class,
					int.class, String.class);

			assertNotNull(new PartTreeDynamoDbQuery(queryMethod, newOperations()));
		}
	}

	@Nested
	@DisplayName("Scan-requiring methods")
	class ScanRequiringTests {

		@Test
		@DisplayName("fails at construction (not at first invocation) without @AllowScan")
		void scanRequiringMethodWithoutAllowScanFailsAtConstructionNotAtFirstInvocation() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = newOperations();
			DynamoDbQueryMethod queryMethod = queryMethodFor("findByRound", String.class);

			// Act & Assert
			InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
					() -> new PartTreeDynamoDbQuery(queryMethod, operations));
			assertAll(() -> assertTrue(ex.getMessage().contains("AllowScan")),
					() -> assertNull(operations.lastCapturedRequest),
					() -> assertNull(operations.lastCapturedScanRequest));
		}

		@Test
		@DisplayName("constructs and executes as a scan with @AllowScan present")
		void scanRequiringMethodWithAllowScanConstructsAndExecutesAsAScan() throws NoSuchMethodException {
			// Arrange
			PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = newOperations();
			DynamoDbQueryMethod queryMethod = queryMethodFor("findAllowedByRound", String.class);

			// Act
			PartTreeDynamoDbQuery query = new PartTreeDynamoDbQuery(queryMethod, operations);
			query.execute(new Object[] { ROUND_VALUE });

			// Assert
			assertAll(() -> assertNotNull(operations.lastCapturedScanRequest),
					() -> assertNotNull(operations.lastCapturedScanRequest.getFilterExpression()),
					() -> assertTrue(operations.lastCapturedScanRequest.getExpressionAttributeValues()
							.containsValue(ROUND_VALUE)));
		}
	}
}
