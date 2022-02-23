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
package io.awspring.cloud.dynamodb.mapping;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;

public class SortKeyTemplateVerifierTest {

	@Table(tableName = "only_template")
	@SortKeyTemplate("MATCH#{year}#{round}")
	static class OnlyTemplateEntity {
		@PartitionKey
		String tournamentId;
		int year;
		String round;
	}

	@Table(tableName = "conflict_base")
	@SortKeyTemplate("MATCH#{year}#{round}")
	static class ConflictBaseTableEntity {
		@PartitionKey
		String tournamentId;
		@SortKey
		String sk;
		int year;
		String round;
	}

	@Table(tableName = "unknown_prop")
	@SortKeyTemplate("MATCH#{doesNotExist}")
	static class UnknownPropertyEntity {
		@PartitionKey
		String pk;
	}

	private static DynamoDbMappingContext newContext() {
		return new DynamoDbMappingContext();
	}

	@Test
	void entityWithOnlyASortKeyTemplateVerifiesCleanly() {
		DynamoDbMappingContext ctx = newContext();
		DynamoDbPersistentEntity<?> entity = ctx.getRequiredPersistentEntity(OnlyTemplateEntity.class);

		assertNotNull(entity);
		assertTrue(entity.getKeySchema().sortKeys().isEmpty());
	}

	@Test
	void bothSortKeyAndTemplateOnBaseTableThrowsNamingTheEntity() {
		DynamoDbMappingContext ctx = newContext();

		MappingException ex = assertThrows(MappingException.class,
				() -> ctx.getRequiredPersistentEntity(ConflictBaseTableEntity.class));
		assertTrue(allMessages(ex).contains(ConflictBaseTableEntity.class.getName()),
				"conflict message should name the entity type; was: " + allMessages(ex));
	}

	@Test
	void templateReferencingUnknownPropertyThrowsAtSameBootstrapPoint() {
		DynamoDbMappingContext ctx = newContext();

		assertThrows(MappingException.class, () -> ctx.getRequiredPersistentEntity(UnknownPropertyEntity.class));
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
}
