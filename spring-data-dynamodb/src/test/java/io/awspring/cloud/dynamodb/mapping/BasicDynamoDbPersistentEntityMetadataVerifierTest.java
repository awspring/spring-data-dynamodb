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

import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SecondaryIndex;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.core.mapping.VerifierMappingExceptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;

public class BasicDynamoDbPersistentEntityMetadataVerifierTest {

	@Test
	public void shouldRejectEntityWithoutPartitionKey() {
		DynamoDbMappingContext context = new DynamoDbMappingContext();

		MappingException exception = Assertions.assertThrows(MappingException.class, () -> {
			context.getPersistentEntity(EntityWithoutPartitionKey.class);
		});

		System.out.println("Exception type: " + exception.getClass().getName());
		System.out.println("Exception message: " + exception.getMessage());
		if (exception.getCause() != null) {
			System.out.println("Cause type: " + exception.getCause().getClass().getName());
			System.out.println("Cause message: " + exception.getCause().getMessage());
		}

		Throwable cause = exception.getCause();
		Assertions.assertNotNull(cause, "Expected a cause");
		Assertions.assertTrue(cause instanceof VerifierMappingExceptions,
				"Expected VerifierMappingExceptions as cause but got " + cause.getClass().getName());
		Assertions.assertTrue(cause.getMessage().contains("must declare exactly one @Id / @PartitionKey property"),
				"Expected message to contain 'must declare exactly one @Id / @PartitionKey property' but got: "
						+ cause.getMessage());
	}

	@Test
	public void shouldAcceptEntityWithPartitionKey() {
		DynamoDbMappingContext context = new DynamoDbMappingContext();

		Assertions.assertDoesNotThrow(() -> {
			context.getPersistentEntity(ValidEntity.class);
		});
	}

	@Test
	public void shouldAcceptEntityWithPartitionKeyAndSortKey() {
		DynamoDbMappingContext context = new DynamoDbMappingContext();

		Assertions.assertDoesNotThrow(() -> {
			context.getPersistentEntity(ValidEntityWithSortKey.class);
		});
	}

	@Test
	public void shouldRejectEntityWithBothTableAndSecondaryIndex() {
		DynamoDbMappingContext context = new DynamoDbMappingContext();

		MappingException exception = Assertions.assertThrows(MappingException.class, () -> {
			context.getPersistentEntity(EntityWithBothTableAndSecondaryIndex.class);
		});

		Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
		Assertions.assertTrue(cause.getMessage().contains("declares both @Table and @SecondaryIndex"),
				"Expected error to mention @Table+@SecondaryIndex conflict, got: " + cause.getMessage());
	}

	@Table(tableName = "conflict")
	@SecondaryIndex(name = "conflict_idx", tableName = "conflict")
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

	@Table(tableName = "invalid_table")
	static class EntityWithoutPartitionKey {
		private String field;

		public String getField() {
			return field;
		}

		public void setField(String field) {
			this.field = field;
		}
	}

	@Table(tableName = "valid_table")
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

	@Table(tableName = "valid_table_with_sk")
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
