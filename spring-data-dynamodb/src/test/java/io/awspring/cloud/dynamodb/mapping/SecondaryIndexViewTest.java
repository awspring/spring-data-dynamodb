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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.IndexKeySchema;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SecondaryIndex;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;

class SecondaryIndexViewTest {

	private static final String TABLE_ARENA = "arena";
	private static final String TABLE_WAREHOUSE = "warehouse";
	private static final String INDEX_BY_STATUS = "by_status";
	private static final String INDEX_TOURNAMENT_REGION = "TournamentRegionIndex";
	private static final String INDEX_ORPHAN = "orphan";
	private static final String ATTR_ROUND = "round";
	private static final String ATTR_CREATED_AT = "createdAt";
	private static final String ATTR_TOURNAMENT_ID = "tournamentId";
	private static final String ATTR_REGION = "region";
	private static final String ATTR_BRACKET = "bracket";
	private static final String ATTR_MATCH_ID = "matchId";

	@Table(tableName = TABLE_ARENA)
	static class ShopRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String gsi1pk;
		String gsi1sk;
	}

	@Table(tableName = TABLE_WAREHOUSE)
	static class WarehouseRow {
		@PartitionKey
		String pk;
	}

	@SecondaryIndex(INDEX_BY_STATUS)
	static class MatchesByRound {
		@PartitionKey
		String round;
		@SortKey
		String createdAt;
		String totalAmount;
	}

	@SecondaryIndex(name = INDEX_BY_STATUS, tableName = TABLE_ARENA)
	static class MatchesByRoundExplicit {
		@PartitionKey
		String round;
		@SortKey
		String createdAt;
	}

	@SecondaryIndex(tableName = TABLE_ARENA, value = INDEX_TOURNAMENT_REGION)
	static class MatchesByTournamentRegion {
		@PartitionKey(order = 0)
		String tournamentId;
		@PartitionKey(order = 1)
		String region;
		@SortKey(order = 0)
		String round;
		@SortKey(order = 1)
		String bracket;
		@SortKey(order = 2)
		String matchId;
	}

	@SecondaryIndex(tableName = TABLE_ARENA)
	static class NoPartitionView {
		@SortKey
		String createdAt;
	}

	@SecondaryIndex(tableName = TABLE_ARENA)
	static class FivePartitionView {
		@PartitionKey(order = 0)
		String a;
		@PartitionKey(order = 1)
		String b;
		@PartitionKey(order = 2)
		String c;
		@PartitionKey(order = 3)
		String d;
		@PartitionKey(order = 4)
		String e;
	}

	@SecondaryIndex(tableName = TABLE_ARENA)
	static class GapSortKeyView {
		@PartitionKey
		String round;
		@SortKey(order = 0)
		String first;
		@SortKey(order = 2)
		String third;
	}

	@SecondaryIndex(tableName = TABLE_ARENA)
	@SortKeyTemplate("ROUND#{round}")
	static class TemplateOnView {
		@PartitionKey
		String pk;
		String round;
	}

	@SecondaryIndex(INDEX_ORPHAN)
	static class OrphanView {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
	}

	private static DynamoDbMappingContext contextWithShopBase() {
		DynamoDbMappingContext ctx = new DynamoDbMappingContext();
		ctx.getRequiredPersistentEntity(ShopRow.class);
		return ctx;
	}

	@Nested
	@DisplayName("Metadata flags")
	class MetadataFlags {

		@Test
		@DisplayName("Base entity is not a secondary index view")
		void isSecondaryIndexView_baseEntity_returnsFalse() {
			DynamoDbPersistentEntity<?> base = contextWithShopBase().getRequiredPersistentEntity(ShopRow.class);

			assertAll(() -> assertFalse(base.isSecondaryIndexView()), () -> assertNull(base.getIndexName()));
		}

		@Test
		@DisplayName("View reports its index name via the value alias")
		void isSecondaryIndexView_annotatedView_returnsTrueWithIndexName() {
			DynamoDbMappingContext ctx = contextWithShopBase();

			DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(MatchesByRound.class);

			assertAll(() -> assertTrue(view.isSecondaryIndexView()),
					() -> assertEquals(INDEX_BY_STATUS, view.getIndexName()));
		}
	}

	@Nested
	@DisplayName("Table resolution")
	class TableResolution {

		@Test
		@DisplayName("View resolves the single distinct base table when no explicit tableName")
		void getTableName_singleBaseTable_resolvesImplicitly() {
			DynamoDbMappingContext ctx = contextWithShopBase();

			DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(MatchesByRound.class);

			assertEquals(TABLE_ARENA, view.getTableName());
		}

		@Test
		@DisplayName("View honours an explicit tableName")
		void getTableName_explicitTableName_usesExplicit() {
			DynamoDbMappingContext ctx = contextWithShopBase();

			DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(MatchesByRoundExplicit.class);

			assertAll(() -> assertEquals(INDEX_BY_STATUS, view.getIndexName()),
					() -> assertEquals(TABLE_ARENA, view.getTableName()));
		}

		@Test
		@DisplayName("Orphan view fails fast when table is requested")
		void getTableName_orphanView_throwsIllegalState() {
			DynamoDbMappingContext ctx = new DynamoDbMappingContext();

			DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(OrphanView.class);

			IllegalStateException ex = assertThrows(IllegalStateException.class, view::getTableName);
			assertTrue(ex.getMessage().contains("cannot resolve its physical table"), ex.getMessage());
		}

		@Test
		@DisplayName("View cannot resolve table when multiple distinct base tables exist")
		void getTableName_multipleBaseTables_throwsIllegalState() {
			DynamoDbMappingContext ctx = new DynamoDbMappingContext();
			ctx.getRequiredPersistentEntity(ShopRow.class);
			ctx.getRequiredPersistentEntity(WarehouseRow.class);

			DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(MatchesByRound.class);

			IllegalStateException ex = assertThrows(IllegalStateException.class, view::getTableName);
			assertTrue(ex.getMessage().contains(TABLE_ARENA) && ex.getMessage().contains(TABLE_WAREHOUSE),
					ex.getMessage());
		}
	}

	@Nested
	@DisplayName("Key schema")
	class KeySchema {

		@Test
		@DisplayName("View key schema is its own local schema")
		void getKeySchema_simpleView_returnsLocalPartitionAndSort() {
			DynamoDbMappingContext ctx = contextWithShopBase();

			DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(MatchesByRound.class);

			IndexKeySchema schema = view.getKeySchema();
			assertAll(() -> assertEquals(1, schema.partitionKeys().size()),
					() -> assertEquals(ATTR_ROUND, schema.partitionKeys().get(0).getName()),
					() -> assertEquals(ATTR_CREATED_AT, schema.singleSortKey().getName()),
					() -> assertEquals(1, view.getKeySchema().partitionKeys().size()));
		}

		@Test
		@DisplayName("Multi-attribute view orders partition and sort components left to right")
		void getKeySchema_multiAttribute_ordersComponentsCorrectly() {
			DynamoDbMappingContext ctx = contextWithShopBase();

			DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(MatchesByTournamentRegion.class);

			IndexKeySchema schema = view.getKeySchema();
			assertAll(
					() -> assertEquals(List.of(ATTR_TOURNAMENT_ID, ATTR_REGION),
							schema.partitionKeys().stream().map(p -> p.getName()).toList()),
					() -> assertEquals(List.of(ATTR_ROUND, ATTR_BRACKET, ATTR_MATCH_ID),
							schema.sortKeys().stream().map(p -> p.getName()).toList()));
		}
	}

	@Nested
	@DisplayName("Validation rejections")
	class ValidationRejections {

		@Test
		@DisplayName("View with no partition key is rejected")
		void validate_noPartitionKey_throwsMappingException() {
			DynamoDbMappingContext ctx = contextWithShopBase();

			MappingException ex = assertThrows(MappingException.class,
					() -> ctx.getRequiredPersistentEntity(NoPartitionView.class));

			assertTrue(allMessages(ex).contains("1-4 @PartitionKey"), allMessages(ex));
		}

		@Test
		@DisplayName("View with more than four partition keys is rejected")
		void validate_fivePartitionKeys_throwsMappingException() {
			DynamoDbMappingContext ctx = contextWithShopBase();

			MappingException ex = assertThrows(MappingException.class,
					() -> ctx.getRequiredPersistentEntity(FivePartitionView.class));

			assertTrue(allMessages(ex).contains("at most 4 @PartitionKey"), allMessages(ex));
		}

		@Test
		@DisplayName("View with non-contiguous sort key order is rejected")
		void validate_gapInSortKeyOrder_throwsMappingException() {
			DynamoDbMappingContext ctx = contextWithShopBase();

			MappingException ex = assertThrows(MappingException.class,
					() -> ctx.getRequiredPersistentEntity(GapSortKeyView.class));

			assertTrue(allMessages(ex).contains("contiguous"), allMessages(ex));
		}

		@Test
		@DisplayName("View declaring a @SortKeyTemplate is rejected")
		void validate_sortKeyTemplateOnView_throwsMappingException() {
			DynamoDbMappingContext ctx = contextWithShopBase();

			MappingException ex = assertThrows(MappingException.class,
					() -> ctx.getRequiredPersistentEntity(TemplateOnView.class));

			assertTrue(allMessages(ex).contains("@SortKeyTemplate"), allMessages(ex));
		}
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
