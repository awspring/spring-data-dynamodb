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
package io.awspring.spring.data.dynamodb.mapping;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;

class SortKeyTemplateVerifierTest {

	private static final String TABLE_ONLY_TEMPLATE = "only_template";
	private static final String TABLE_CONFLICT_BASE = "conflict_base";
	private static final String TABLE_UNKNOWN_PROP = "unknown_prop";

	private static DynamoDbMappingContext newContext() {
		return new DynamoDbMappingContext();
	}

	private static String allMessages(Throwable throwable) {
		StringBuilder builder = new StringBuilder();
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current.getMessage() != null) {
				builder.append(current.getMessage()).append('\n');
			}
			for (Throwable suppressed : current.getSuppressed()) {
				if (suppressed.getMessage() != null) {
					builder.append(suppressed.getMessage()).append('\n');
				}
			}
		}
		return builder.toString();
	}

	@Nested
	@DisplayName("ValidEntities")
	class ValidEntities {

		@Test
		@DisplayName("entity with only a sort-key template verifies cleanly")
		void getEntity_onlyTemplate_verifies() {
			DynamoDbMappingContext ctx = newContext();

			DynamoDbPersistentEntity<?> entity = ctx.getRequiredPersistentEntity(OnlyTemplateEntity.class);

			assertAll(() -> assertNotNull(entity), () -> assertTrue(entity.getKeySchema().sortKeys().isEmpty()));
		}
	}

	@Nested
	@DisplayName("ValidationRejections")
	class ValidationRejections {

		@Test
		@DisplayName("both @SortKey and @SortKeyTemplate on base table throws naming the entity")
		void getEntity_sortKeyAndTemplate_throwsNamingEntity() {
			DynamoDbMappingContext ctx = newContext();

			MappingException ex = assertThrows(MappingException.class,
					() -> ctx.getRequiredPersistentEntity(ConflictBaseTableEntity.class));

			assertTrue(allMessages(ex).contains(ConflictBaseTableEntity.class.getName()),
					"conflict message should name the entity type; was: " + allMessages(ex));
		}

		@Test
		@DisplayName("template referencing unknown property throws at bootstrap")
		void getEntity_unknownTemplateProperty_throws() {
			DynamoDbMappingContext ctx = newContext();

			assertThrows(MappingException.class, () -> ctx.getRequiredPersistentEntity(UnknownPropertyEntity.class));
		}
	}

	// --- Test fixtures ---

	@Table(tableName = TABLE_ONLY_TEMPLATE)
	@SortKeyTemplate("MATCH#{year}#{round}")
	static class OnlyTemplateEntity {
		@PartitionKey
		String tournamentId;
		int year;
		String round;
	}

	@Table(tableName = TABLE_CONFLICT_BASE)
	@SortKeyTemplate("MATCH#{year}#{round}")
	static class ConflictBaseTableEntity {
		@PartitionKey
		String tournamentId;
		@SortKey
		String sk;
		int year;
		String round;
	}

	@Table(tableName = TABLE_UNKNOWN_PROP)
	@SortKeyTemplate("MATCH#{doesNotExist}")
	static class UnknownPropertyEntity {
		@PartitionKey
		String pk;
	}
}
