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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.awspring.spring.data.dynamodb.LocalStackTestContainer;
import io.awspring.spring.data.dynamodb.core.DynamoDbTemplate;
import io.awspring.spring.data.dynamodb.core.EntityQueryResult;
import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.AggregateItem;
import io.awspring.spring.data.dynamodb.core.mapping.AggregateTable;
import io.awspring.spring.data.dynamodb.core.mapping.Column;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.request.DynamoDbQueryRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

public class GlobalSecondaryTemplateIndexTest extends LocalStackTestContainer {

	private static final String TABLE_NAME = "arena_aggregate";
	private static final String GSI_NAME = "GSI1";
	private static final String TOURNAMENT_PK = "TOURNAMENT#1";
	private static final String TOURNAMENT_NAME = "Winter Championship";
	private static final String PLAYER_ALICE = "Alice";
	private static final String PLAYER_BOB = "Bob";

	private DynamoDbClient dynamoDbClient;
	private DynamoDbTemplate dynamoDbTemplate;

	@BeforeEach
	void setUp() {
		dynamoDbClient = DynamoDbClient.builder().region(Region.of(localstack.getRegion()))
				.endpointOverride(localstack.getEndpoint())
				.credentialsProvider(StaticCredentialsProvider
						.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
				.build();

		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();

		dynamoDbTemplate = new DynamoDbTemplate(dynamoDbClient, converter);

		recreateTable();
	}

	private void recreateTable() {
		deleteTableIfExists();

		CreateTableRequest request = CreateTableRequest.builder().tableName(TABLE_NAME).attributeDefinitions(
				AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
				AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build(),
				AttributeDefinition.builder().attributeName("gsi1pk").attributeType(ScalarAttributeType.S).build(),
				AttributeDefinition.builder().attributeName("gsi1sk").attributeType(ScalarAttributeType.S).build())
				.keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
						KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
				.globalSecondaryIndexes(GlobalSecondaryIndex.builder().indexName(GSI_NAME)
						.keySchema(KeySchemaElement.builder().attributeName("gsi1pk").keyType(KeyType.HASH).build(),
								KeySchemaElement.builder().attributeName("gsi1sk").keyType(KeyType.RANGE).build())
						.projection(Projection.builder().projectionType(ProjectionType.ALL).build())
						.provisionedThroughput(
								ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
						.build())
				.provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build();

		dynamoDbClient.createTable(request);
		waitForTableActive();
	}

	private void deleteTableIfExists() {
		boolean exists;
		try {
			dynamoDbClient.describeTable(b -> b.tableName(TABLE_NAME));
			exists = true;
		}
		catch (ResourceNotFoundException ex) {
			exists = false;
		}

		if (!exists) {
			return;
		}

		dynamoDbClient.deleteTable(b -> b.tableName(TABLE_NAME));

		org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300))
				.until(() -> {
					try {
						dynamoDbClient.describeTable(b -> b.tableName(TABLE_NAME));
						return false;
					}
					catch (ResourceNotFoundException ex) {
						return true;
					}
				});
	}

	private void waitForTableActive() {
		org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300))
				.until(() -> "ACTIVE".equals(
						dynamoDbClient.describeTable(b -> b.tableName(TABLE_NAME)).table().tableStatusAsString()));
	}

	private DynamoDbQueryRequest tournamentQuery() {
		return DynamoDbQueryRequest.request().withKeyConditionExpression("gsi1pk = :pk")
				.withExpressionAttributeValues(Map.of(":pk", TOURNAMENT_PK)).build();
	}

	private void seedFullTournament() {
		dynamoDbTemplate.save(ArenaItem.tournament("1", TOURNAMENT_NAME));
		dynamoDbTemplate.save(ArenaItem.player("1", "p1", PLAYER_ALICE));
		dynamoDbTemplate.save(ArenaItem.player("1", "p2", PLAYER_BOB));
		dynamoDbTemplate.save(ArenaItem.match("1", "m1", "SEMIFINAL"));
		dynamoDbTemplate.save(ArenaItem.match("1", "m2", "FINAL"));
	}

	public static class TournamentData {

		@PartitionKey
		@Column("gsi1pk")
		private String partitionKey;

		@SortKey
		@Column("gsi1sk")
		private String sortKey;

		@Column("tournamentName")
		private String name;

		public TournamentData() {
		}

		public TournamentData(String partitionKey, String sortKey, String name) {
			this.partitionKey = partitionKey;
			this.sortKey = sortKey;
			this.name = name;
		}

		public String getPartitionKey() {
			return partitionKey;
		}

		public String getSortKey() {
			return sortKey;
		}

		public String getName() {
			return name;
		}
	}

	public static class PlayerData {

		@PartitionKey
		@Column("gsi1pk")
		private String partitionKey;

		@SortKey
		@Column("gsi1sk")
		private String sortKey;

		@Column("playerName")
		private String name;

		public PlayerData() {
		}

		public PlayerData(String partitionKey, String sortKey, String name) {
			this.partitionKey = partitionKey;
			this.sortKey = sortKey;
			this.name = name;
		}

		public String getPartitionKey() {
			return partitionKey;
		}

		public String getSortKey() {
			return sortKey;
		}

		public String getName() {
			return name;
		}
	}

	public static class MatchData {

		@PartitionKey
		@Column("gsi1pk")
		private String partitionKey;

		@SortKey
		@Column("gsi1sk")
		private String sortKey;
		private String matchId;
		private String round;

		public MatchData() {
		}

		public MatchData(String partitionKey, String sortKey, String matchId, String round) {
			this.partitionKey = partitionKey;
			this.sortKey = sortKey;
			this.matchId = matchId;
			this.round = round;
		}

		public String getPartitionKey() {
			return partitionKey;
		}

		public String getSortKey() {
			return sortKey;
		}

		public String getMatchId() {
			return matchId;
		}

		public String getRound() {
			return round;
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

		@Column("tournamentName")
		private String tournamentName;

		@Column("playerName")
		private String playerName;

		@Column("matchId")
		private String matchId;

		@Column("round")
		private String round;

		public ArenaItem() {
		}

		static ArenaItem tournament(String tournamentId, String name) {
			ArenaItem item = new ArenaItem();
			item.pk = "TOURNAMENT#" + tournamentId;
			item.sk = "TOURNAMENT#" + tournamentId;
			item.gsi1pk = "TOURNAMENT#" + tournamentId;
			item.gsi1sk = "TOURNAMENT#" + tournamentId;
			item.tournamentName = name;
			return item;
		}

		static ArenaItem player(String tournamentId, String playerId, String name) {
			ArenaItem item = new ArenaItem();
			item.pk = "TOURNAMENT#" + tournamentId;
			item.sk = "PLAYER#" + playerId;
			item.gsi1pk = "TOURNAMENT#" + tournamentId;
			item.gsi1sk = "PLAYER#" + playerId;
			item.playerName = name;
			return item;
		}

		static ArenaItem match(String tournamentId, String matchId, String round) {
			ArenaItem item = new ArenaItem();
			item.pk = "TOURNAMENT#" + tournamentId;
			item.sk = "MATCH#" + matchId;
			item.gsi1pk = "TOURNAMENT#" + tournamentId;
			item.gsi1sk = "MATCH#" + matchId;
			item.matchId = matchId;
			item.round = round;
			return item;
		}
	}

	@AggregateTable(tableName = TABLE_NAME, indexName = GSI_NAME, partitionKey = "gsi1pk", sortKey = "gsi1sk")
	public static class TournamentAggregate {

		@AggregateItem(regex = "TOURNAMENT#[^#]+")
		private TournamentData tournament;

		@AggregateItem(regex = "PLAYER#[^#]+")
		private List<PlayerData> players;

		@AggregateItem(regex = "MATCH#[^#]+")
		private List<MatchData> matches;

		public TournamentData getTournament() {
			return tournament;
		}

		public List<PlayerData> getPlayers() {
			return players;
		}

		public List<MatchData> getMatches() {
			return matches;
		}
	}

	@Nested
	@DisplayName("Aggregate reading from GSI")
	class AggregateReading {

		@Test
		@DisplayName("reads heterogeneous rows from a GSI and routes them into typed aggregate slots")
		void queryAggregate_fullPartition_routesAllRowTypes() {
			seedFullTournament();

			EntityQueryResult<TournamentAggregate> result = dynamoDbTemplate.queryAggregate(TournamentAggregate.class,
					tournamentQuery(), null);
			TournamentAggregate aggregate = result.getEntity();

			assertAll("full tournament aggregate from GSI", () -> assertNotNull(aggregate),
					() -> assertEquals(TOURNAMENT_PK, aggregate.getTournament().sortKey),
					() -> assertEquals(TOURNAMENT_NAME, aggregate.getTournament().getName()),
					() -> assertEquals(2, aggregate.getPlayers().size()),
					() -> assertEquals(2, aggregate.getMatches().size()),
					() -> assertEquals(PLAYER_ALICE, aggregate.getPlayers().get(1).getName()),
					() -> assertEquals(PLAYER_BOB, aggregate.getPlayers().get(0).getName()));
		}

		@Test
		@DisplayName("uses the GSI sort key (not the base-table SK) for aggregate item matching")
		void queryAggregate_partialPartition_matchesOnGsiSortKey() {
			dynamoDbTemplate.save(ArenaItem.player("1", "p1", PLAYER_ALICE));
			dynamoDbTemplate.save(ArenaItem.match("1", "m1", "SEMIFINAL"));

			TournamentAggregate aggregate = dynamoDbTemplate
					.queryAggregate(TournamentAggregate.class, tournamentQuery(), null).getEntity();

			assertAll("partial aggregate routing by GSI sort key", () -> assertNull(aggregate.getTournament()),
					() -> assertEquals(1, aggregate.getPlayers().size()),
					() -> assertEquals(1, aggregate.getMatches().size()),
					() -> assertEquals(PLAYER_ALICE, aggregate.getPlayers().get(0).getName()),
					() -> assertEquals("m1", aggregate.getMatches().get(0).getMatchId()));
		}
	}

	@Nested
	@DisplayName("Sort key independence")
	class SortKeyIndependence {

		@Test
		@DisplayName("base-table SK can differ from GSI SK without affecting aggregate routing")
		void queryAggregate_divergentBaseAndGsiSk_routesCorrectly() {
			ArenaItem item = ArenaItem.match("1", "m1", "SEMIFINAL");
			item.sk = "BASE#SOMETHING";
			item.gsi1sk = "MATCH#m1";
			dynamoDbTemplate.save(item);

			TournamentAggregate aggregate = dynamoDbTemplate
					.queryAggregate(TournamentAggregate.class, tournamentQuery(), null).getEntity();

			assertAll("aggregate routes on GSI SK, not base-table SK",
					() -> assertEquals(1, aggregate.getMatches().size()),
					() -> assertEquals("m1", aggregate.getMatches().get(0).getMatchId()));
		}
	}

	@Nested
	@DisplayName("Read-only enforcement")
	class ReadOnlyEnforcement {

		@Test
		@DisplayName("saving an @AggregateTable entity throws InvalidDataAccessApiUsageException")
		void save_aggregateTable_throwsException() {
			TournamentAggregate aggregate = new TournamentAggregate();

			assertThrows(InvalidDataAccessApiUsageException.class, () -> dynamoDbTemplate.save(aggregate));
		}
	}
}
