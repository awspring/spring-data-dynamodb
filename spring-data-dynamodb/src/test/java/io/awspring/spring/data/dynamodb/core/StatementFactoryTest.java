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
package io.awspring.spring.data.dynamodb.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.request.DynamoDbQueryRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbScanRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

@DisplayName("StatementFactory -- builds AWS SDK request objects from entities")
class StatementFactoryTest {

	private DynamoDbMappingContext mappingContext;
	private StatementFactory statementFactory;

	@BeforeEach
	void setUp() {
		this.mappingContext = new DynamoDbMappingContext();
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		this.statementFactory = new StatementFactory(converter);
	}

	@Test
	void existsByKeyBuildsProjectionOnlyGetItemForPartitionKeyOnly() {
		DynamoDbPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(Match.class);

		GetItemRequest request = statementFactory.existsByKey("cust-1", null, "orders", entity);

		assertEquals("#__pk", request.projectionExpression());
		assertTrue(request.expressionAttributeNames().containsKey("#__pk"));
		assertEquals("tournamentId", request.expressionAttributeNames().get("#__pk"));
		assertFalse(request.consistentRead());
		assertTrue(request.key().containsKey("tournamentId"));
		assertFalse(request.key().containsKey("matchId"));
	}

	@Test
	void existsByKeyIncludesSortKeyWhenProvided() {
		DynamoDbPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(Match.class);

		GetItemRequest request = statementFactory.existsByKey("cust-1", "match-1", "orders", entity);

		assertEquals("#__pk", request.projectionExpression());
		assertTrue(request.key().containsKey("tournamentId"));
		assertTrue(request.key().containsKey("matchId"));
	}

	@Test
	@DisplayName("strongly consistent GSI queries are rejected before reaching the SDK")
	void stronglyConsistentGsiQueriesAreRejectedBeforeReachingTheSdk() {
		DynamoDbPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(Match.class);
		DynamoDbQueryRequest request = DynamoDbQueryRequest.Builder.request().withIndexName("gsi1")
				.withConsistentRead(Boolean.TRUE).build();

		InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
				() -> statementFactory.query("orders", entity, request, null));

		assertTrue(ex.getMessage().contains("eventually consistent"));
	}

	@Test
	@DisplayName("strongly consistent GSI scans are rejected before reaching the SDK")
	void stronglyConsistentGsiScansAreRejectedBeforeReachingTheSdk() {
		DynamoDbPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(Match.class);
		DynamoDbScanRequest request = DynamoDbScanRequest.Builder.builder().withIndexName("gsi1")
				.withConsistentRead(true).build();

		InvalidDataAccessApiUsageException ex = assertThrows(InvalidDataAccessApiUsageException.class,
				() -> statementFactory.scan("orders", request, entity));

		assertTrue(ex.getMessage().contains("eventually consistent"));
	}

	@Table(tableName = "orders")
	static class Match {
		@PartitionKey
		String tournamentId;
		@SortKey
		String matchId;
		String round;
	}
}
