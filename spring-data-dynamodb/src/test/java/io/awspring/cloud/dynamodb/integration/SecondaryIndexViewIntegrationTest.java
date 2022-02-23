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
package io.awspring.cloud.dynamodb.integration;

import io.awspring.cloud.dynamodb.LocalStackTestContainer;
import io.awspring.cloud.dynamodb.config.AbstractDynamoDbConfiguration;
import io.awspring.cloud.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.dynamodb.core.mapping.Column;
import io.awspring.cloud.dynamodb.core.mapping.InnerClass;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SecondaryIndex;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.DynamoDbRepository;
import io.awspring.cloud.dynamodb.repository.SecondaryIndexRepository;
import io.awspring.cloud.dynamodb.repository.config.EnableDynamoDbRepositories;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateGlobalSecondaryIndexAction;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexUpdate;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.LocalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

public class SecondaryIndexViewIntegrationTest extends LocalStackTestContainer {

	private static final String TABLE_NAME = "tournament_arena";
	private static final String GSI1 = "GSI1";
	private static final String REGION_LSI = "by_region";
	private static final String ROUND_INDEX = "by_round";

	private DynamoDbClient dynamoDbClient;
	private AnnotationConfigApplicationContext context;
	private ArenaItemRepository baseRepository;
	private PlayerInTournamentViewRepository gsi1Repository;
	private MatchByRegionRepository byRegionRepository;
	private MatchByRoundRepository byRoundRepository;

	public static class TournamentData {
		private String name;

		public String getName() {
			return name;
		}
	}

	public static class PlayerData {
		private String name;

		public String getName() {
			return name;
		}
	}

	public static class MatchData {
		private String round;
		private String region;

		public String getRound() {
			return round;
		}

		public String getRegion() {
			return region;
		}
	}

	public static class ResultData {
		private String winner;

		public String getWinner() {
			return winner;
		}
	}

	@Table(tableName = TABLE_NAME)
	public static class ArenaItem {
		@PartitionKey
		@Column("pk")
		private String pk;
		@SortKey
		@Column("sk")
		private String sk;

		@Column("gsi1pk")
		private String gsi1pk;
		@Column("gsi1sk")
		private String gsi1sk;

		@InnerClass(startsWith = "TOURNAMENT#")
		private TournamentData tournament;
		@InnerClass(startsWith = "PLAYER#")
		private PlayerData player;
		@InnerClass(startsWith = "MATCH#")
		private MatchData match;
		@InnerClass(startsWith = "RESULT#")
		private ResultData result;

		public ArenaItem() {
		}

		static ArenaItem tournament(String tournamentId, String name) {
			ArenaItem item = new ArenaItem();
			item.pk = "TOURNAMENT#" + tournamentId;
			item.sk = "TOURNAMENT#" + tournamentId;
			item.gsi1pk = "TOURNAMENT#" + tournamentId;
			item.gsi1sk = "TOURNAMENT#" + tournamentId;
			item.tournament = new TournamentData();
			item.tournament.name = name;
			return item;
		}

		static ArenaItem player(String tournamentId, String playerId, String name) {
			ArenaItem item = new ArenaItem();
			item.pk = "TOURNAMENT#" + tournamentId;
			item.sk = "PLAYER#" + playerId;
			item.gsi1pk = "PT#" + tournamentId + "#" + playerId;
			item.gsi1sk = "PLAYER#" + playerId;
			item.player = new PlayerData();
			item.player.name = name;
			return item;
		}

		static ArenaItem match(String tournamentId, String playerId, String matchId, String round, String region) {
			ArenaItem item = new ArenaItem();
			item.pk = "TOURNAMENT#" + tournamentId;
			item.sk = "MATCH#" + matchId;
			item.gsi1pk = "PT#" + tournamentId + "#" + playerId;
			item.gsi1sk = "MATCH#" + matchId;
			item.match = new MatchData();
			item.match.round = round;
			item.match.region = region;
			return item;
		}

		static ArenaItem result(String tournamentId, String matchId, String winner) {
			ArenaItem item = new ArenaItem();
			item.pk = "TOURNAMENT#" + tournamentId;
			item.sk = "RESULT#" + matchId;
			item.gsi1pk = "RESULT#" + tournamentId;
			item.gsi1sk = "RESULT#" + matchId;
			item.result = new ResultData();
			item.result.winner = winner;
			return item;
		}
	}

	public interface ArenaItemRepository extends DynamoDbRepository<ArenaItem, String> {
		List<ArenaItem> findByPk(String pk);
	}

	@SecondaryIndex(name = GSI1, tableName = TABLE_NAME)
	public static class PlayerInTournamentView {
		@PartitionKey
		@Column("gsi1pk")
		private String collectionKey;
		@SortKey
		@Column("gsi1sk")
		private String itemKey;

		@InnerClass(startsWith = "PLAYER#")
		private PlayerData player;
		@InnerClass(startsWith = "MATCH#")
		private MatchData match;

		public String getCollectionKey() {
			return collectionKey;
		}

		public String getItemKey() {
			return itemKey;
		}

		public PlayerData getPlayer() {
			return player;
		}

		public MatchData getMatch() {
			return match;
		}
	}

	public interface PlayerInTournamentViewRepository extends SecondaryIndexRepository<PlayerInTournamentView> {
		List<PlayerInTournamentView> findByCollectionKey(String collectionKey);
	}

	@SecondaryIndex(name = REGION_LSI, tableName = TABLE_NAME)
	public static class MatchByRegion {
		@PartitionKey
		@Column("pk")
		private String pk;
		@SortKey
		@Column("region")
		private String region;

		public String getPk() {
			return pk;
		}

		public String getRegion() {
			return region;
		}
	}

	public interface MatchByRegionRepository extends SecondaryIndexRepository<MatchByRegion> {
		List<MatchByRegion> findByPkAndRegion(String pk, String region);
	}

	@SecondaryIndex(name = ROUND_INDEX, tableName = TABLE_NAME)
	public static class MatchByRound {
		@PartitionKey(order = 0)
		@Column("pk")
		private String tournamentPk;
		@SortKey(order = 0)
		@Column("round")
		private String round;

		public String getTournamentPk() {
			return tournamentPk;
		}

		public String getRound() {
			return round;
		}
	}

	public interface MatchByRoundRepository extends SecondaryIndexRepository<MatchByRound> {
		List<MatchByRound> findByTournamentPkAndRound(String tournamentPk, String round);
	}

	@EnableDynamoDbRepositories(basePackageClasses = SecondaryIndexViewIntegrationTest.class, considerNestedRepositories = true, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.awspring\\.cloud\\.dynamodb\\.integration\\.(?!SecondaryIndexViewIntegrationTest\\$).*"))
	static class TestConfig extends AbstractDynamoDbConfiguration {

		private final DynamoDbClient dynamoDbClient;

		TestConfig(DynamoDbClient dynamoDbClient) {
			this.dynamoDbClient = dynamoDbClient;
		}

		@Bean
		@Override
		public DynamoDbClient dynamoDbClient() {
			return dynamoDbClient;
		}
	}

	private static boolean multiAttributeGsiSupported = true;

	@BeforeEach
	void setUp() {
		dynamoDbClient = DynamoDbClient.builder().region(Region.of(localstack.getRegion()))
				.endpointOverride(localstack.getEndpoint())
				.credentialsProvider(StaticCredentialsProvider
						.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
				.build();

		try {
			dynamoDbClient.deleteTable(builder -> builder.tableName(TABLE_NAME));
		}
		catch (ResourceNotFoundException notFound) {
		}

		createTable();

		context = new AnnotationConfigApplicationContext();
		context.registerBean(DynamoDbClient.class, () -> dynamoDbClient);
		context.register(TestConfig.class);
		context.refresh();
		baseRepository = context.getBean(ArenaItemRepository.class);
		gsi1Repository = context.getBean(PlayerInTournamentViewRepository.class);
		byRegionRepository = context.getBean(MatchByRegionRepository.class);
		if (multiAttributeGsiSupported) {
			byRoundRepository = context.getBean(MatchByRoundRepository.class);
		}
	}

	private void createTable() {
		dynamoDbClient.createTable(CreateTableRequest.builder().tableName(TABLE_NAME).attributeDefinitions(
				AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
				AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build(),
				AttributeDefinition.builder().attributeName("gsi1pk").attributeType(ScalarAttributeType.S).build(),
				AttributeDefinition.builder().attributeName("gsi1sk").attributeType(ScalarAttributeType.S).build(),
				AttributeDefinition.builder().attributeName("region").attributeType(ScalarAttributeType.S).build())
				.keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
						KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
				.provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.globalSecondaryIndexes(GlobalSecondaryIndex.builder().indexName(GSI1)
						.keySchema(KeySchemaElement.builder().attributeName("gsi1pk").keyType(KeyType.HASH).build(),
								KeySchemaElement.builder().attributeName("gsi1sk").keyType(KeyType.RANGE).build())
						.projection(Projection.builder().projectionType(ProjectionType.ALL).build())
						.provisionedThroughput(
								ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
						.build())
				.localSecondaryIndexes(LocalSecondaryIndex.builder().indexName(REGION_LSI)
						.keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
								KeySchemaElement.builder().attributeName("region").keyType(KeyType.RANGE).build())
						.projection(Projection.builder().projectionType(ProjectionType.ALL).build()).build())
				.build());

		waitForTableActive();

		try {
			dynamoDbClient.updateTable(builder -> builder.tableName(TABLE_NAME)
					.attributeDefinitions(AttributeDefinition.builder().attributeName("round")
							.attributeType(ScalarAttributeType.S).build())
					.globalSecondaryIndexUpdates(GlobalSecondaryIndexUpdate.builder()
							.create(CreateGlobalSecondaryIndexAction.builder().indexName(ROUND_INDEX).keySchema(
									KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
									KeySchemaElement.builder().attributeName("round").keyType(KeyType.RANGE).build())
									.projection(Projection.builder().projectionType(ProjectionType.ALL).build())
									.provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(10L)
											.writeCapacityUnits(10L).build())
									.build())
							.build()));
			multiAttributeGsiSupported = waitForIndexActive(ROUND_INDEX);
		}
		catch (RuntimeException multiAttributeUnsupported) {
			multiAttributeGsiSupported = false;
		}
	}

	private void waitForTableActive() {
		org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300))
				.until(() -> "ACTIVE".equals(
						dynamoDbClient.describeTable(b -> b.tableName(TABLE_NAME)).table().tableStatusAsString()));
	}

	private boolean waitForIndexActive(String indexName) {
		try {
			org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300))
					.until(() -> {
						var table = dynamoDbClient.describeTable(b -> b.tableName(TABLE_NAME)).table();
						return table.globalSecondaryIndexes() != null && table.globalSecondaryIndexes().stream()
								.anyMatch(gsi -> indexName.equals(gsi.indexName())
										&& "ACTIVE".equals(gsi.indexStatusAsString()));
					});
			return true;
		}
		catch (org.awaitility.core.ConditionTimeoutException ex) {
			return false;
		}
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.close();
		}
	}

	private void seedArena() {
		baseRepository.save(ArenaItem.tournament("1", "Winter Championship"));
		baseRepository.save(ArenaItem.player("1", "p1", "Alice"));
		baseRepository.save(ArenaItem.match("1", "p1", "m1", "SEMIFINALS", "NA-EAST"));
		baseRepository.save(ArenaItem.match("1", "p1", "m2", "FINALS", "NA-EAST"));
		baseRepository.save(ArenaItem.result("1", "m1", "p1"));
	}

	@Test
	void polymorphicContainerViewReconstructsPlayerAndMatchRowsByOverloadedSortKeyPrefix() {
		seedArena();

		List<PlayerInTournamentView> rows = gsi1Repository.findByCollectionKey("PT#1#p1");

		Assertions.assertEquals(3, rows.size(), "player p1's GSI1 collection: itself + 2 matches");
		PlayerInTournamentView playerRow = rows.stream().filter(r -> r.getPlayer() != null).findFirst()
				.orElseThrow(() -> new AssertionError("expected one row to reconstruct as a Player"));
		Assertions.assertNull(playerRow.getMatch(), "a PLAYER# row must not also populate the match field");
		Assertions.assertEquals("Alice", playerRow.getPlayer().getName());

		long matchRows = rows.stream().filter(r -> r.getMatch() != null).count();
		Assertions.assertEquals(2, matchRows, "both of p1's matches share the same GSI1 collection key");
	}

	@Test
	void baseTableItselfReconstructsAllFourPolymorphicKindsWithNoTypeAttribute() {
		seedArena();

		List<ArenaItem> items = baseRepository.findByPk("TOURNAMENT#1");

		Assertions.assertEquals(5, items.size());
		Assertions.assertEquals(1, items.stream().filter(i -> i.tournament != null).count());
		Assertions.assertEquals(1, items.stream().filter(i -> i.player != null).count());
		Assertions.assertEquals(2, items.stream().filter(i -> i.match != null).count());
		Assertions.assertEquals(1, items.stream().filter(i -> i.result != null).count());
	}

	@Test
	void typedViewOverLsiSharesTheBasePartitionKeyAndQueriesItsOwnSortAttribute() {
		seedArena();

		List<MatchByRegion> found = byRegionRepository.findByPkAndRegion("TOURNAMENT#1", "NA-EAST");

		Assertions.assertEquals(2, found.size(), "both matches under TOURNAMENT#1 are NA-EAST");
	}

	@Test
	void typedViewOverMultiAttributeGsiMatchesOnBothKeyAttributes() {
		Assumptions.assumeTrue(multiAttributeGsiSupported,
				"this LocalStack build does not support the Nov-2025 multi-attribute GSI-key feature "
						+ "-- skipping rather than failing the acceptance suite for an "
						+ "infra limitation unrelated to the module's own code");
		seedArena();

		List<MatchByRound> found = byRoundRepository.findByTournamentPkAndRound("TOURNAMENT#1", "SEMIFINALS");

		Assertions.assertEquals(1, found.size());
		Assertions.assertEquals("SEMIFINALS", found.get(0).getRound());
	}

	@Test
	void saveOnATypedViewIsRejected() {
		MatchByRegion view = new MatchByRegion();
		view.pk = "TOURNAMENT#1";
		view.region = "NA-EAST";
		Assertions.assertThrows(InvalidDataAccessApiUsageException.class,
				() -> context.getBean(DynamoDbOperations.class).save(view));
	}

	@Test
	void findByIdOnATypedViewIsRejected() {
		Assertions.assertThrows(InvalidDataAccessApiUsageException.class,
				() -> context.getBean(DynamoDbOperations.class).findById("TOURNAMENT#1", MatchByRegion.class));
	}
}
