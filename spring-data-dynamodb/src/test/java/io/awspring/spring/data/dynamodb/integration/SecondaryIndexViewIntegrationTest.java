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
package io.awspring.spring.data.dynamodb.integration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.awspring.spring.data.dynamodb.LocalStackTestContainer;
import io.awspring.spring.data.dynamodb.config.AbstractDynamoDbConfiguration;
import io.awspring.spring.data.dynamodb.core.DynamoDbOperations;
import io.awspring.spring.data.dynamodb.core.mapping.Column;
import io.awspring.spring.data.dynamodb.core.mapping.Embedded;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SecondaryIndex;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepository;
import io.awspring.spring.data.dynamodb.repository.ExpressionName;
import io.awspring.spring.data.dynamodb.repository.Query;
import io.awspring.spring.data.dynamodb.repository.SecondaryIndexRepository;
import io.awspring.spring.data.dynamodb.repository.config.EnableDynamoDbRepositories;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.repository.query.Param;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
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
	private static final String TOURNAMENT_ID = "1";
	private static final String TOURNAMENT_PK = "TOURNAMENT#" + TOURNAMENT_ID;
	private static final String PLAYER_ID = "p1";
	private static final String PLAYER_NAME = "Alice";
	private static final String MATCH_REGION = "NA-EAST";

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

		@Embedded(startsWith = "TOURNAMENT#")
		private TournamentData tournament;
		@Embedded(startsWith = "PLAYER#")
		private PlayerData player;
		@Embedded(startsWith = "MATCH#")
		private MatchData match;
		@Embedded(startsWith = "RESULT#")
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

		@Embedded(startsWith = "PLAYER#")
		private PlayerData player;
		@Embedded(startsWith = "MATCH#")
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

		Window<PlayerInTournamentView> findByCollectionKey(String collectionKey, ScrollPosition position, Limit limit);

		@Query(filterExpression = "#name = :name", names = @ExpressionName(name = "#name", value = "name"), allowScan = true)
		List<PlayerInTournamentView> findByPlayerName(@Param("name") String name);
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

	@EnableDynamoDbRepositories(basePackageClasses = SecondaryIndexViewIntegrationTest.class, considerNestedRepositories = true, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.awspring\\.spring\\.data\\.dynamodb\\.integration\\.(?!SecondaryIndexViewIntegrationTest\\$).*"))
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
		baseRepository.save(ArenaItem.tournament(TOURNAMENT_ID, "Winter Championship"));
		baseRepository.save(ArenaItem.player(TOURNAMENT_ID, PLAYER_ID, PLAYER_NAME));
		baseRepository.save(ArenaItem.match(TOURNAMENT_ID, PLAYER_ID, "m1", "SEMIFINALS", MATCH_REGION));
		baseRepository.save(ArenaItem.match(TOURNAMENT_ID, PLAYER_ID, "m2", "FINALS", MATCH_REGION));
		baseRepository.save(ArenaItem.result(TOURNAMENT_ID, "m1", PLAYER_ID));
	}

	@Nested
	@DisplayName("GSI heterogeneous rows")
	class GsiHeterogeneousRows {

		@Test
		@DisplayName("GSI view reconstructs player and match rows by overloaded sort-key prefix")
		void findByCollectionKey_playerCollection_routesByPrefix() {
			seedArena();

			List<PlayerInTournamentView> rows = gsi1Repository
					.findByCollectionKey("PT#" + TOURNAMENT_ID + "#" + PLAYER_ID);

			assertEquals(3, rows.size(), "player p1's GSI1 collection: itself + 2 matches");
			PlayerInTournamentView playerRow = rows.stream().filter(r -> r.getPlayer() != null).findFirst()
					.orElseThrow(() -> new AssertionError("expected one row to reconstruct as a Player"));
			assertAll("player row routing",
					() -> assertNull(playerRow.getMatch(), "a PLAYER# row must not also populate the match field"),
					() -> assertEquals(PLAYER_NAME, playerRow.getPlayer().getName()));

			long matchRows = rows.stream().filter(r -> r.getMatch() != null).count();
			assertEquals(2, matchRows, "both of p1's matches share the same GSI1 collection key");
		}

		@Test
		@DisplayName("filter-only query scans the declared GSI rather than the base table")
		void findByPlayerName_filterOnly_scansGsi() {
			seedArena();
			dynamoDbClient.putItem(builder -> builder.tableName(TABLE_NAME)
					.item(Map.of("pk", AttributeValue.builder().s("BASE#ONLY").build(), "sk",
							AttributeValue.builder().s("PLAYER#base-only").build(), "name",
							AttributeValue.builder().s(PLAYER_NAME).build())));

			List<PlayerInTournamentView> found = gsi1Repository.findByPlayerName(PLAYER_NAME);

			assertAll("GSI scan excludes the matching sparse base-table row", () -> assertEquals(1, found.size()),
					() -> assertEquals("PT#" + TOURNAMENT_ID + "#" + PLAYER_ID, found.get(0).getCollectionKey()),
					() -> assertEquals(PLAYER_NAME, found.get(0).getPlayer().getName()));
		}

		@Test
		@DisplayName("Window pagination resumes across every GSI row without duplicates")
		void findByCollectionKey_window_pagesAcrossGsi() {
			seedArena();
			String collectionKey = "PT#" + TOURNAMENT_ID + "#" + PLAYER_ID;
			List<String> itemKeys = new ArrayList<>();
			ScrollPosition position = ScrollPosition.keyset();
			int pages = 0;
			Window<PlayerInTournamentView> window;

			do {
				window = gsi1Repository.findByCollectionKey(collectionKey, position, Limit.of(1));
				window.forEach(row -> itemKeys.add(row.getItemKey()));
				pages++;
				if (window.hasNext()) {
					position = window.positionAt(window.getContent().size() - 1);
				}
			}
			while (window.hasNext() && pages < 10);

			assertEquals(List.of("MATCH#m1", "MATCH#m2", "PLAYER#p1"), itemKeys,
					"every GSI row must be returned once in index sort-key order");
			assertFalse(window.hasNext(), "the terminal Window must be exhausted");
		}

		@Test
		@DisplayName("base table reconstructs all four heterogeneous row shapes with no type attribute")
		void findByPk_allKinds_reconstructsEach() {
			seedArena();

			List<ArenaItem> items = baseRepository.findByPk(TOURNAMENT_PK);

			assertAll("all heterogeneous row shapes present", () -> assertEquals(5, items.size()),
					() -> assertEquals(1, items.stream().filter(i -> i.tournament != null).count()),
					() -> assertEquals(1, items.stream().filter(i -> i.player != null).count()),
					() -> assertEquals(2, items.stream().filter(i -> i.match != null).count()),
					() -> assertEquals(1, items.stream().filter(i -> i.result != null).count()));
		}
	}

	@Nested
	@DisplayName("LSI view")
	class LsiView {

		@Test
		@DisplayName("typed view over LSI shares the base partition key and queries its own sort attribute")
		void findByPkAndRegion_lsi_returnsByRegion() {
			seedArena();

			List<MatchByRegion> found = byRegionRepository.findByPkAndRegion(TOURNAMENT_PK, MATCH_REGION);

			assertEquals(2, found.size(), "both matches under TOURNAMENT#1 are NA-EAST");
		}
	}

	@Nested
	@DisplayName("Multi-attribute GSI")
	class MultiAttributeGsi {

		@Test
		@DisplayName("typed view over multi-attribute GSI matches on both key attributes")
		void findByTournamentPkAndRound_multiAttrGsi_matchesBothKeys() {
			Assumptions.assumeTrue(multiAttributeGsiSupported,
					"this LocalStack build does not support the Nov-2025 multi-attribute GSI-key feature "
							+ "-- skipping rather than failing the acceptance suite for an "
							+ "infra limitation unrelated to the module's own code");
			seedArena();

			List<MatchByRound> found = byRoundRepository.findByTournamentPkAndRound(TOURNAMENT_PK, "SEMIFINALS");

			assertAll("multi-attribute GSI query", () -> assertEquals(1, found.size()),
					() -> assertEquals("SEMIFINALS", found.get(0).getRound()));
		}
	}

	@Nested
	@DisplayName("Read-only enforcement")
	class ReadOnlyEnforcement {

		@Test
		@DisplayName("save on a typed view is rejected")
		void save_secondaryIndexView_throws() {
			MatchByRegion view = new MatchByRegion();
			view.pk = TOURNAMENT_PK;
			view.region = MATCH_REGION;

			assertThrows(InvalidDataAccessApiUsageException.class,
					() -> context.getBean(DynamoDbOperations.class).save(view));
		}

		@Test
		@DisplayName("findById on a typed view is rejected")
		void findById_secondaryIndexView_throws() {
			assertThrows(InvalidDataAccessApiUsageException.class,
					() -> context.getBean(DynamoDbOperations.class).findById(TOURNAMENT_PK, MatchByRegion.class));
		}
	}
}
