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

import static org.junit.jupiter.api.Assertions.*;

import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SecondaryIndex;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.core.mapping.VerifierMappingExceptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;

class BasicDynamoDbPersistentEntityMetadataVerifierTest {

	private static final String TABLE_INVALID = "invalid_table";
	private static final String TABLE_VALID = "valid_table";
	private static final String TABLE_VALID_WITH_SK = "valid_table_with_sk";
	private static final String TABLE_CONFLICT = "conflict";
	private static final String INDEX_CONFLICT = "conflict_idx";

	@Nested
	@DisplayName("Validation rejections")
	class ValidationRejections {

		@Test
		@DisplayName("Throws MappingException when entity has no @PartitionKey")
		void getPersistentEntity_noPartitionKey_throwsMappingException() {
			DynamoDbMappingContext context = new DynamoDbMappingContext();

			MappingException exception = assertThrows(MappingException.class,
					() -> context.getPersistentEntity(EntityWithoutPartitionKey.class));

			Throwable cause = exception.getCause();
			assertAll(() -> assertNotNull(cause, "Expected a cause"),
					() -> assertInstanceOf(VerifierMappingExceptions.class, cause,
							"Expected VerifierMappingExceptions as cause but got "
									+ (cause != null ? cause.getClass().getName() : "null")),
					() -> assertTrue(
							cause.getMessage().contains("must declare exactly one @Id / @PartitionKey property"),
							"Expected message to contain 'must declare exactly one @Id / @PartitionKey property' but got: "
									+ cause.getMessage()));
		}

		@Test
		@DisplayName("Throws MappingException when entity has both @Table and @SecondaryIndex")
		void getPersistentEntity_bothTableAndSecondaryIndex_throwsMappingException() {
			DynamoDbMappingContext context = new DynamoDbMappingContext();

			MappingException exception = assertThrows(MappingException.class,
					() -> context.getPersistentEntity(EntityWithBothTableAndSecondaryIndex.class));

			Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
			assertTrue(
					cause.getMessage()
							.contains("declares more than one of @Table, @SecondaryIndex and @AggregateTable"),
					"Expected error to mention @Table+@SecondaryIndex conflict, got: " + cause.getMessage());
		}
	}

	@Nested
	@DisplayName("Valid entities")
	class ValidEntities {

		@Test
		@DisplayName("Accepts entity with @PartitionKey")
		void getPersistentEntity_withPartitionKey_succeeds() {
			DynamoDbMappingContext context = new DynamoDbMappingContext();

			assertDoesNotThrow(() -> context.getPersistentEntity(ValidEntity.class));
		}

		@Test
		@DisplayName("Accepts entity with @PartitionKey and @SortKey")
		void getPersistentEntity_withPartitionKeyAndSortKey_succeeds() {
			DynamoDbMappingContext context = new DynamoDbMappingContext();

			assertDoesNotThrow(() -> context.getPersistentEntity(ValidEntityWithSortKey.class));
		}
	}

	@Table(tableName = TABLE_CONFLICT)
	@SecondaryIndex(name = INDEX_CONFLICT, tableName = TABLE_CONFLICT)
	static class EntityWithBothTableAndSecondaryIndex {
		@PartitionKey
		private String id;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}
	}

	@Table(tableName = TABLE_INVALID)
	static class EntityWithoutPartitionKey {
		private String field;

		public String getField() {
			return field;
		}

		public void setField(String field) {
			this.field = field;
		}
	}

	@Table(tableName = TABLE_VALID)
	static class ValidEntity {
		@PartitionKey
		private String id;

		private String field;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getField() {
			return field;
		}

		public void setField(String field) {
			this.field = field;
		}
	}

	@Table(tableName = TABLE_VALID_WITH_SK)
	static class ValidEntityWithSortKey {
		@PartitionKey
		private String id;

		@SortKey
		private String sortKey;

		private String field;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getSortKey() {
			return sortKey;
		}

		public void setSortKey(String sortKey) {
			this.sortKey = sortKey;
		}

		public String getField() {
			return field;
		}

		public void setField(String field) {
			this.field = field;
		}
	}
}
