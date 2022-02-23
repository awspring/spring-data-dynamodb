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
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.MappingException;

public class SecondaryIndexViewTest {

	@Table(tableName = "arena")
	static class ShopRow {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String gsi1pk;
		String gsi1sk;
	}

	@Table(tableName = "warehouse")
	static class WarehouseRow {
		@PartitionKey
		String pk;
	}

	@SecondaryIndex("by_status")
	static class MatchesByRound {
		@PartitionKey
		String round;
		@SortKey
		String createdAt;
		String totalAmount;
	}

	@SecondaryIndex(name = "by_status", tableName = "arena")
	static class MatchesByRoundExplicit {
		@PartitionKey
		String round;
		@SortKey
		String createdAt;
	}

	@SecondaryIndex(tableName = "arena", value = "TournamentRegionIndex")
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

	@SecondaryIndex(tableName = "arena")
	static class NoPartitionView {
		@SortKey
		String createdAt;
	}

	@SecondaryIndex(tableName = "arena")
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

	@SecondaryIndex(tableName = "arena")
	static class GapSortKeyView {
		@PartitionKey
		String round;
		@SortKey(order = 0)
		String first;
		@SortKey(order = 2)
		String third;
	}

	@SecondaryIndex(tableName = "arena")
	@SortKeyTemplate("ROUND#{round}")
	static class TemplateOnView {
		@PartitionKey
		String pk;
		String round;
	}

	@SecondaryIndex("orphan")
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

	@Test
	void baseEntityIsNotAView() {
		DynamoDbPersistentEntity<?> base = contextWithShopBase().getRequiredPersistentEntity(ShopRow.class);
		assertFalse(base.isSecondaryIndexView());
		assertNull(base.getIndexName());
	}

	@Test
	void viewReportsItsIndexNameViaTheValueAlias() {
		DynamoDbMappingContext ctx = contextWithShopBase();
		DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(MatchesByRound.class);

		assertTrue(view.isSecondaryIndexView());
		assertEquals("by_status", view.getIndexName());
	}

	@Test
	void viewResolvesTheSingleDistinctBaseTableWhenNoExplicitTableName() {
		DynamoDbMappingContext ctx = contextWithShopBase();
		DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(MatchesByRound.class);

		assertEquals("arena", view.getTableName());
	}

	@Test
	void viewHonoursAnExplicitTableName() {
		DynamoDbMappingContext ctx = contextWithShopBase();
		DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(MatchesByRoundExplicit.class);

		assertEquals("by_status", view.getIndexName());
		assertEquals("arena", view.getTableName());
	}

	@Test
	void viewKeySchemaIsItsOwnLocalSchema() {
		DynamoDbMappingContext ctx = contextWithShopBase();
		DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(MatchesByRound.class);

		IndexKeySchema schema = view.getKeySchema();
		assertEquals(1, schema.partitionKeys().size());
		assertEquals("round", schema.partitionKeys().get(0).getName());
		assertEquals("createdAt", schema.singleSortKey().getName());
		assertEquals(1, view.getKeySchema().partitionKeys().size());
	}

	@Test
	void multiAttributeViewOrdersPartitionAndSortComponentsLeftToRight() {
		DynamoDbMappingContext ctx = contextWithShopBase();
		DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(MatchesByTournamentRegion.class);

		IndexKeySchema schema = view.getKeySchema();
		assertEquals(List.of("tournamentId", "region"), schema.partitionKeys().stream().map(p -> p.getName()).toList());
		assertEquals(List.of("round", "bracket", "matchId"), schema.sortKeys().stream().map(p -> p.getName()).toList());
	}

	@Test
	void viewWithNoPartitionKeyIsRejected() {
		DynamoDbMappingContext ctx = contextWithShopBase();
		MappingException ex = assertThrows(MappingException.class,
				() -> ctx.getRequiredPersistentEntity(NoPartitionView.class));
		assertTrue(allMessages(ex).contains("1-4 @PartitionKey"), allMessages(ex));
	}

	@Test
	void viewWithMoreThanFourPartitionKeysIsRejected() {
		DynamoDbMappingContext ctx = contextWithShopBase();
		MappingException ex = assertThrows(MappingException.class,
				() -> ctx.getRequiredPersistentEntity(FivePartitionView.class));
		assertTrue(allMessages(ex).contains("at most 4 @PartitionKey"), allMessages(ex));
	}

	@Test
	void viewWithANonContiguousSortKeyOrderIsRejected() {
		DynamoDbMappingContext ctx = contextWithShopBase();
		MappingException ex = assertThrows(MappingException.class,
				() -> ctx.getRequiredPersistentEntity(GapSortKeyView.class));
		assertTrue(allMessages(ex).contains("contiguous"), allMessages(ex));
	}

	@Test
	void viewDeclaringASortKeyTemplateIsRejected() {
		DynamoDbMappingContext ctx = contextWithShopBase();
		MappingException ex = assertThrows(MappingException.class,
				() -> ctx.getRequiredPersistentEntity(TemplateOnView.class));
		assertTrue(allMessages(ex).contains("@SortKeyTemplate"), allMessages(ex));
	}

	@Test
	void viewThatCannotResolveItsTableFailsFastWhenTheTableIsRequested() {
		DynamoDbMappingContext ctx = new DynamoDbMappingContext();
		DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(OrphanView.class);

		IllegalStateException ex = assertThrows(IllegalStateException.class, view::getTableName);
		assertTrue(ex.getMessage().contains("cannot resolve its physical table"), ex.getMessage());
	}

	@Test
	void viewCannotResolveTableWhenMultipleDistinctBaseTablesExist() {
		DynamoDbMappingContext ctx = new DynamoDbMappingContext();
		ctx.getRequiredPersistentEntity(ShopRow.class);
		ctx.getRequiredPersistentEntity(WarehouseRow.class);
		DynamoDbPersistentEntity<?> view = ctx.getRequiredPersistentEntity(MatchesByRound.class);

		IllegalStateException ex = assertThrows(IllegalStateException.class, view::getTableName);
		assertTrue(ex.getMessage().contains("arena") && ex.getMessage().contains("warehouse"), ex.getMessage());
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
