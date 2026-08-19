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
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.LocalStackTestContainer;
import io.awspring.spring.data.dynamodb.core.DynamoDbTemplate;
import io.awspring.spring.data.dynamodb.core.EntityQueryResult;
import io.awspring.spring.data.dynamodb.core.EntityReadResult;
import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.events.DynamoDbBeforeSaveCallback;
import io.awspring.spring.data.dynamodb.entities.ArenaRow;
import io.awspring.spring.data.dynamodb.entities.Player;
import io.awspring.spring.data.dynamodb.entities.PlayerCard;
import io.awspring.spring.data.dynamodb.entities.Team;
import io.awspring.spring.data.dynamodb.entities.arena.MatchAggregate;
import io.awspring.spring.data.dynamodb.entities.arena.MatchSK;
import io.awspring.spring.data.dynamodb.entities.arena.MatchStatus;
import io.awspring.spring.data.dynamodb.entities.arena.MatchTable;
import io.awspring.spring.data.dynamodb.entities.arena.PlayerContact;
import io.awspring.spring.data.dynamodb.entities.arena.Venue;
import io.awspring.spring.data.dynamodb.mapping.PlayerCardEntity;
import io.awspring.spring.data.dynamodb.request.DynamoDbPageRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbQueryRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.callback.EntityCallbacks;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

public class DynamoDbTemplateTest extends LocalStackTestContainer {

	private static final String PLAYER_CARD_ENTITY_TABLE = "playerCardEntity";
	private static final String PLAYER_CARD_TABLE = "playerCard";
	private static final String ARENA_TABLE = "arena";
	private static final String ARENA_TABLE_EXTENDED = "arena_table";
	private static final String PLAYER_PK = "PLAYER#myUser";
	private static final String MATCH_SK = "MATCH#15236";
	private static final String PLAYER_SK = "PLAYER#myUser";

	private DynamoDbTemplate dynamoDbTemplate;
	private DynamoDbClient dynamoDbClient;
	private MappingDynamoDbConverter mappingDynamoDbConverter;

	@BeforeEach
	void setUp() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingDynamoDbConverter = new MappingDynamoDbConverter(mappingContext);
		mappingDynamoDbConverter.afterPropertiesSet();
		dynamoDbClient = DynamoDbClient.builder().region(Region.of(localstack.getRegion()))
				.endpointOverride(localstack.getEndpoint())
				.credentialsProvider(StaticCredentialsProvider
						.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
				.build();
		EntityCallbacks callbacks = EntityCallbacks.create();
		callbacks.addEntityCallback((DynamoDbBeforeSaveCallback<Object>) (entity, tableName) -> {
			Assertions.assertNotNull(tableName);
			return entity;
		});
		dynamoDbTemplate = new DynamoDbTemplate(dynamoDbClient, mappingDynamoDbConverter);
		dynamoDbTemplate.setEntityCallbacks(callbacks);

		createTableIfAbsent(CreateTableRequest.builder().tableName(PLAYER_CARD_ENTITY_TABLE)
				.attributeDefinitions(
						AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build())
				.keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
				.provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build());

		createTableIfAbsent(CreateTableRequest.builder().tableName(PLAYER_CARD_TABLE)
				.attributeDefinitions(
						AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build())
				.keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
				.provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build());

		createTableIfAbsent(CreateTableRequest.builder().tableName(ARENA_TABLE).attributeDefinitions(
				AttributeDefinition.builder().attributeName("partitionKey").attributeType(ScalarAttributeType.S)
						.build(),
				AttributeDefinition.builder().attributeName("sortKey").attributeType(ScalarAttributeType.S).build())
				.keySchema(KeySchemaElement.builder().attributeName("partitionKey").keyType(KeyType.HASH).build(),
						KeySchemaElement.builder().attributeName("sortKey").keyType(KeyType.RANGE).build())
				.provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build());
	}

	private void createTableIfAbsent(CreateTableRequest request) {
		try {
			dynamoDbClient.createTable(request);
		}
		catch (ResourceInUseException alreadyExists) {
		}
	}

	private void clearTable(String tableName, String... keyAttributes) {
		for (Map<String, AttributeValue> item : dynamoDbClient.scan(ScanRequest.builder().tableName(tableName).build())
				.items()) {
			Map<String, AttributeValue> key = new HashMap<>();
			for (String attr : keyAttributes) {
				key.put(attr, item.get(attr));
			}
			dynamoDbClient.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(key).build());
		}
	}

	private Map<String, AttributeValue> getItemByKey(String tableName, Map<String, AttributeValue> key) {
		return dynamoDbClient.getItem(GetItemRequest.builder().key(key).tableName(tableName).build()).item();
	}

	@Nested
	@DisplayName("Save operations")
	class SaveOperations {

		@Test
		@DisplayName("save persists entity with all fields to DynamoDB")
		void save_entityWithAllFields_persistsCorrectly() {
			LocalDate testDate = LocalDate.now();
			PlayerCardEntity playerCardEntity = new PlayerCardEntity("testID", testDate);

			dynamoDbTemplate.save(playerCardEntity);

			Map<String, AttributeValue> key = new HashMap<>();
			key.put("id", AttributeValue.builder().s("testID").build());
			Map<String, AttributeValue> stored = getItemByKey(PLAYER_CARD_ENTITY_TABLE, key);

			assertAll("saved entity attributes", () -> assertEquals(stored.get("id").s(), playerCardEntity.getId()),
					() -> assertEquals(LocalDate.parse(stored.get("registeredOn").s()),
							playerCardEntity.getRegisteredOn()));
		}

		@Test
		@DisplayName("save persists entity with null fields as DynamoDB NULL type")
		void save_entityWithNullFields_persistsAsNull() {
			PlayerCardEntity playerCardEntity = new PlayerCardEntity("anotherId", null);

			dynamoDbTemplate.save(playerCardEntity);

			Map<String, AttributeValue> key = new HashMap<>();
			key.put("id", AttributeValue.builder().s("anotherId").build());
			Map<String, AttributeValue> stored = getItemByKey(PLAYER_CARD_ENTITY_TABLE, key);

			assertAll("null field handling", () -> assertEquals(stored.get("id").s(), playerCardEntity.getId()),
					() -> assertTrue(stored.get("registeredOn").nul()));
		}

		@Test
		@DisplayName("save persists ArenaRow with flattened inner class properties")
		void save_arenaRowWithInnerClass_flattensCorrectly() {
			createTableIfAbsent(CreateTableRequest.builder().tableName(ARENA_TABLE_EXTENDED).attributeDefinitions(
					AttributeDefinition.builder().attributeName("partitionKey").attributeType(ScalarAttributeType.S)
							.build(),
					AttributeDefinition.builder().attributeName("sortKey").attributeType(ScalarAttributeType.S).build())
					.keySchema(KeySchemaElement.builder().attributeName("partitionKey").keyType(KeyType.HASH).build(),
							KeySchemaElement.builder().attributeName("sortKey").keyType(KeyType.RANGE).build())
					.provisionedThroughput(
							ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
					.build());

			Team team = new Team("Cloud Esports", "CLE", "NA");
			Player player = new Player("Faker", "Lee Sang-hyeok", LocalDate.now(), "KR");
			ArenaRow arenaRow = new ArenaRow("test", "sort", team, null);
			ArenaRow arenaRow2 = new ArenaRow("test2", "sort2", null, player);

			dynamoDbTemplate.save(arenaRow);
			dynamoDbTemplate.save(arenaRow2);

			Map<String, AttributeValue> key = new HashMap<>();
			key.put("partitionKey", AttributeValue.builder().s("test").build());
			key.put("sortKey", AttributeValue.builder().s("sort").build());
			Map<String, AttributeValue> storedTeamRow = getItemByKey(ARENA_TABLE_EXTENDED, key);

			assertAll("arena row with team",
					() -> assertEquals(storedTeamRow.get("partitionKey").s(), arenaRow.getPartitionKey()),
					() -> assertEquals(storedTeamRow.get("sortKey").s(), arenaRow.getSortKey()));

			ArenaRow toCompare = dynamoDbTemplate.findById("test2", "sort2", ArenaRow.class);
			key.put("partitionKey", AttributeValue.builder().s("test2").build());
			key.put("sortKey", AttributeValue.builder().s("sort2").build());
			Map<String, AttributeValue> storedPlayerRow = getItemByKey(ARENA_TABLE_EXTENDED, key);

			assertAll("arena row with player",
					() -> assertEquals(toCompare.getPlayer().getGamerTag(), arenaRow2.getPlayer().getGamerTag()),
					() -> assertEquals(null, storedPlayerRow.get("name")));
		}
	}

	@Nested
	@DisplayName("Single-table design (polymorphic container)")
	class SingleTableDesign {

		@Test
		@DisplayName("save and query polymorphic items in a single table with PartiQL and DynamoDB query")
		void saveAndQuery_polymorphicItems_routesCorrectly() {
			clearTable(ARENA_TABLE, "partitionKey", "sortKey");

			Venue venue = new Venue("Zagreb", 10000L, "Trg bana Josipa Jelacica", "Hrvatska");
			LocalDate date = LocalDate.now();
			MatchSK match = new MatchSK("myUser", UUID.randomUUID(), MatchStatus.COMPLETED, date, venue,
					"SOME#123512512");
			PlayerContact playerContact = new PlayerContact("myUser", "Josip Jelacic", "fake_email", date, venue);
			MatchTable shopTable1 = new MatchTable(PLAYER_PK, PLAYER_SK, null, playerContact);
			MatchTable shopTable2 = new MatchTable(PLAYER_PK, MATCH_SK, match, null);

			dynamoDbTemplate.save(shopTable1);
			dynamoDbTemplate.save(shopTable2);

			EntityReadResult<List<MatchTable>> sqlTypeQueryResult = dynamoDbTemplate
					.executeStatement("Select * from arena", null, MatchTable.class);

			DynamoDbQueryRequest dynamoDBQueryRequest = DynamoDbQueryRequest.Builder.request()
					.withKeyConditionExpression("partitionKey = :pk")
					.withExpressionAttributeValues(Map.of(":pk", PLAYER_PK)).build();
			EntityQueryResult<List<MatchTable>> properDynamoQuery = dynamoDbTemplate.query(MatchTable.class,
					dynamoDBQueryRequest, null);
			DynamoDbQueryRequest dq = DynamoDbQueryRequest.request().withKeyConditionExpression("partitionKey = :pk")
					.withExpressionAttributeValues(Map.of(":pk", PLAYER_PK)).build();

			EntityQueryResult<List<MatchTable>> secondQuery = dynamoDbTemplate.query(MatchTable.class, dq, null);

			MatchTable matchTable = dynamoDbTemplate.findById(PLAYER_PK, MATCH_SK, MatchTable.class);

			assertAll("polymorphic query results",
					() -> assertIterableEquals(List.of(shopTable1, shopTable2), properDynamoQuery.getEntity()),
					() -> assertIterableEquals(List.of(shopTable2, shopTable1), sqlTypeQueryResult.getEntity()),
					() -> assertEquals(matchTable, shopTable2),
					() -> assertEquals(properDynamoQuery.getEntity().size(), secondQuery.getEntity().size()));

			Map<String, AttributeValue> rawKey = new HashMap<>();
			rawKey.put("partitionKey", AttributeValue.builder().s(PLAYER_PK).build());
			rawKey.put("sortKey", AttributeValue.builder().s(MATCH_SK).build());
			Map<String, AttributeValue> rawOrderItem = getItemByKey(ARENA_TABLE, rawKey);

			assertAll("raw item flattening", () -> assertEquals("myUser", rawOrderItem.get("tournamentId").s()),
					() -> assertNotNull(rawOrderItem.get("matchId")),
					() -> assertNotNull(rawOrderItem.get("GLOBAL_SK_1")),
					() -> assertFalse(rawOrderItem.containsKey("match")),
					() -> assertFalse(rawOrderItem.containsKey("playerContact")));

			dynamoDbTemplate.delete(shopTable1);
			dynamoDbTemplate.delete(MatchTable.class, shopTable2.getPartitionKey(), shopTable2.getSortKey());

			EntityReadResult<List<MatchTable>> afterDeleteResult = dynamoDbTemplate
					.executeStatement("Select * from arena", null, MatchTable.class);
			assertEquals(0, afterDeleteResult.getEntity().size());
		}
	}

	@Nested
	@DisplayName("Aggregate query")
	class AggregateQuery {

		@Test
		@DisplayName("queryAggregate folds multiple item types into typed aggregate slots")
		void queryAggregate_multipleItemTypes_foldsCorrectly() {
			clearTable(ARENA_TABLE, "partitionKey", "sortKey");

			Venue venue = new Venue("Zagreb", 10000L, "Trg bana Josipa Jelacica", "Hrvatska");
			LocalDate date = LocalDate.now();
			MatchSK match = new MatchSK("myUser", UUID.randomUUID(), MatchStatus.COMPLETED, date, venue,
					"SOME#123512512");
			PlayerContact playerContact = new PlayerContact("myUser", "Josip Jelacic", "fake_email", date, venue);
			MatchTable matchTable1 = new MatchTable(PLAYER_PK, PLAYER_SK, null, playerContact);
			MatchTable matchTable2 = new MatchTable(PLAYER_PK, MATCH_SK, match, null);
			MatchTable matchTable3 = new MatchTable(PLAYER_PK, "MATCH#15678", match, null);
			dynamoDbTemplate.save(matchTable1);
			dynamoDbTemplate.save(matchTable2);
			dynamoDbTemplate.save(matchTable3);

			dynamoDbTemplate.executeStatement("Select * from arena", null, MatchTable.class);

			DynamoDbQueryRequest dynamoDBQueryRequest = DynamoDbQueryRequest.Builder.request()
					.withKeyConditionExpression("partitionKey = :pk")
					.withExpressionAttributeValues(Map.of(":pk", PLAYER_PK)).build();
			EntityQueryResult<List<MatchTable>> properDynamoQuery = dynamoDbTemplate.query(MatchTable.class,
					dynamoDBQueryRequest, null);

			DynamoDbQueryRequest dq = DynamoDbQueryRequest.request().withKeyConditionExpression("partitionKey = :pk")
					.withExpressionAttributeValues(Map.of(":pk", PLAYER_PK)).build();

			EntityQueryResult<MatchAggregate> secondQuery = dynamoDbTemplate.queryAggregate(MatchAggregate.class, dq,
					null);

			MatchTable matchTable = dynamoDbTemplate.findById(PLAYER_PK, MATCH_SK, MatchTable.class);

			assertAll("aggregate routing",
					() -> assertEquals(matchTable2.getMatch(), secondQuery.getEntity().getMatch().get(0)),
					() -> assertEquals(matchTable3.getMatch(), secondQuery.getEntity().getMatch().get(1)));

			Map<String, AttributeValue> rawKey = new HashMap<>();
			rawKey.put("partitionKey", AttributeValue.builder().s(PLAYER_PK).build());
			rawKey.put("sortKey", AttributeValue.builder().s(MATCH_SK).build());
			Map<String, AttributeValue> rawOrderItem = getItemByKey(ARENA_TABLE, rawKey);

			assertAll("raw item structure", () -> assertEquals("myUser", rawOrderItem.get("tournamentId").s()),
					() -> assertNotNull(rawOrderItem.get("matchId")),
					() -> assertNotNull(rawOrderItem.get("GLOBAL_SK_1")),
					() -> assertFalse(rawOrderItem.containsKey("match")),
					() -> assertFalse(rawOrderItem.containsKey("playerContact")));

			dynamoDbTemplate.delete(matchTable1);
			dynamoDbTemplate.delete(MatchTable.class, matchTable2.getPartitionKey(), matchTable2.getSortKey());
			dynamoDbTemplate.delete(matchTable3);

			EntityReadResult<List<MatchTable>> sqlTypeQueryResult = dynamoDbTemplate
					.executeStatement("Select * from arena", null, MatchTable.class);
			assertEquals(0, sqlTypeQueryResult.getEntity().size());
		}
	}

	@Nested
	@DisplayName("Delete operations")
	class DeleteOperations {

		@Test
		@DisplayName("delete removes the item and subsequent get returns empty")
		void delete_existingEntity_removesFromTable() {
			LocalDate testDate = LocalDate.now();
			PlayerCardEntity playerCardEntity = new PlayerCardEntity("testID2", testDate);

			dynamoDbTemplate.save(playerCardEntity);

			Map<String, AttributeValue> key = new HashMap<>();
			key.put("id", AttributeValue.builder().s("testID2").build());
			Map<String, AttributeValue> stored = getItemByKey(PLAYER_CARD_ENTITY_TABLE, key);

			assertAll("entity exists before delete", () -> assertEquals(stored.get("id").s(), playerCardEntity.getId()),
					() -> assertEquals(LocalDate.parse(stored.get("registeredOn").s()),
							playerCardEntity.getRegisteredOn()));

			dynamoDbTemplate.delete(playerCardEntity);

			Map<String, AttributeValue> afterDelete = getItemByKey(PLAYER_CARD_ENTITY_TABLE, key);
			assertEquals(0L, afterDelete.size());
		}
	}

	@Nested
	@DisplayName("Read operations")
	class ReadOperations {

		@Test
		@DisplayName("findById reads back entity with collections intact")
		void findById_entityWithCollections_readsCorrectly() {
			LocalDate testDate = LocalDate.now();
			PlayerCardEntity playerCardEntity = new PlayerCardEntity("testID3", testDate,
					Arrays.asList("test1", "test2"), Arrays.asList("099", "095"));

			dynamoDbTemplate.save(playerCardEntity);

			PlayerCardEntity readClass = dynamoDbTemplate.findById(playerCardEntity.getId(), PlayerCardEntity.class);

			assertAll("read-back with collections", () -> assertEquals(readClass.getId(), playerCardEntity.getId()),
					() -> assertEquals(readClass.getRegisteredOn(), playerCardEntity.getRegisteredOn()),
					() -> assertEquals(readClass.getTags(), Arrays.asList("test1", "test2")),
					() -> assertEquals(2, readClass.getAliases().size()));
		}

		@Test
		@DisplayName("findById reads entity with nested maps and lists from raw attributes")
		void findById_rawAttributeWithNestedMaps_readsCorrectly() {
			List<AttributeValue> listOfValues = new ArrayList<>();
			listOfValues.add(AttributeValue.builder().s("Jhon").build());
			listOfValues.add(AttributeValue.builder().s("Doe").build());
			List<AttributeValue> listOfAnimals = new ArrayList<>();
			listOfAnimals.add(AttributeValue.builder().s("cat").build());
			listOfAnimals.add(AttributeValue.builder().s("doggo").build());
			Map<String, AttributeValue> mapOfList = new HashMap<>();
			mapOfList.put("myName", AttributeValue.builder().l(listOfValues).build());
			mapOfList.put("animals", AttributeValue.builder().l(listOfAnimals).build());

			Map<String, AttributeValue> attributeValueMap = new HashMap<>();
			attributeValueMap.put("id", AttributeValue.builder().s("2").build());
			attributeValueMap.put("contactHandle", AttributeValue.builder().s("some random phone number").build());
			attributeValueMap.put("points", AttributeValue.builder().n(BigDecimal.valueOf(200).toString()).build());
			attributeValueMap.put("achievements", AttributeValue.builder().l(listOfValues).build());
			attributeValueMap.put("statsByGame", AttributeValue.builder().m(mapOfList).build());
			dynamoDbClient
					.putItem(PutItemRequest.builder().item(attributeValueMap).tableName(PLAYER_CARD_TABLE).build());

			PlayerCard playerCard = dynamoDbTemplate.findById("2", PlayerCard.class);

			assertAll("nested map/list reading", () -> assertEquals("2", playerCard.getId()),
					() -> assertEquals("some random phone number", playerCard.profile().getContactHandle()),
					() -> assertEquals(200, playerCard.profile().points),
					() -> assertEquals("cat", playerCard.profile().statsByGame.get("animals").get(0)),
					() -> assertEquals("doggo", playerCard.profile().statsByGame.get("animals").get(1)),
					() -> assertEquals("Jhon", playerCard.profile().achievements.get(0)));
		}
	}

	@Nested
	@DisplayName("Update operations")
	class UpdateOperations {

		@Test
		@DisplayName("update modifies fields and adds collection elements")
		void update_modifyFields_persistsChanges() {
			LocalDate testDate = LocalDate.now();
			PlayerCardEntity playerCardEntity = new PlayerCardEntity("testID4", testDate);

			dynamoDbTemplate.save(playerCardEntity);

			LocalDate newDate = testDate.plusDays(1);
			playerCardEntity.setRegisteredOn(newDate);
			playerCardEntity.getTags().add("new");
			dynamoDbTemplate.update(playerCardEntity);

			Map<String, AttributeValue> key = new HashMap<>();
			key.put("id", AttributeValue.builder().s("testID4").build());
			Map<String, AttributeValue> stored = getItemByKey(PLAYER_CARD_ENTITY_TABLE, key);

			assertAll("updated fields", () -> assertEquals(stored.get("id").s(), playerCardEntity.getId()),
					() -> assertEquals(LocalDate.parse(stored.get("registeredOn").s()), newDate),
					() -> assertEquals(stored.get("tags").l().size(), playerCardEntity.getTags().size()));
		}
	}

	@Nested
	@DisplayName("PartiQL support")
	class PartiqlSupport {

		@Test
		@DisplayName("executeStatement returns all matching items")
		void executeStatement_selectAll_returnsAllItems() {
			clearTable(PLAYER_CARD_ENTITY_TABLE, "id");
			LocalDate testDate = LocalDate.now();
			PlayerCardEntity playerCardEntity = new PlayerCardEntity("randomId", testDate);

			dynamoDbTemplate.save(playerCardEntity);

			EntityReadResult<List<PlayerCardEntity>> list = dynamoDbTemplate
					.executeStatement("Select * from playerCardEntity", null, PlayerCardEntity.class);

			assertAll("PartiQL result", () -> assertEquals(1L, list.getEntity().size()),
					() -> assertEquals(list.getEntity().get(0).getId(), playerCardEntity.getId()),
					() -> assertEquals(list.getEntity().get(0).getTags(), playerCardEntity.getTags()),
					() -> assertEquals(list.getEntity().get(0).getAliases(), playerCardEntity.getAliases()),
					() -> assertEquals(list.getEntity().get(0).getRegisteredOn(), playerCardEntity.getRegisteredOn()));
		}
	}

	@Nested
	@DisplayName("Batch operations")
	class BatchOperations {

		@Test
		@DisplayName("saveAll persists more than 25 items (exceeds single batch limit)")
		void saveAll_over25Items_chunksCorrectly() {
			clearTable(PLAYER_CARD_ENTITY_TABLE, "id");
			LocalDate testDate = LocalDate.now();
			List<PlayerCardEntity> arrayList = new ArrayList<>();
			for (int i = 0; i <= 100; i++) {
				arrayList.add(new PlayerCardEntity("randomId" + i, testDate));
				if (i % 20 == 0) {
					dynamoDbTemplate.saveAll(arrayList);
					arrayList = new ArrayList<>();
				}
			}

			EntityReadResult<List<PlayerCardEntity>> list = dynamoDbTemplate
					.executeStatement("Select * from playerCardEntity", null, PlayerCardEntity.class);

			assertEquals(101, list.getEntity().size());
		}
	}

	@Nested
	@DisplayName("Query pagination")
	class QueryPagination {

		@Test
		@DisplayName("paginated query returns correct page size and advances cursor")
		void query_withPageSize_returnsCorrectCountAndAdvancesCursor() {
			clearTable(ARENA_TABLE, "partitionKey", "sortKey");
			MatchSK match = new MatchSK("myUser", UUID.randomUUID(), MatchStatus.COMPLETED, null, null,
					"SOME#123512512");
			List<MatchTable> entities = new ArrayList<>();
			for (int i = 1; i <= 100; i++) {
				if (i % 20 == 0) {
					dynamoDbTemplate.saveAll(entities);
					entities.clear();
				}
				var shopEntity = new MatchTable(PLAYER_PK, "MATCH#" + i, match, null);
				entities.add(shopEntity);
			}

			DynamoDbQueryRequest dynamoDBQueryRequest = DynamoDbQueryRequest.Builder.request()
					.withKeyConditionExpression("partitionKey = :pk")
					.withExpressionAttributeValues(Map.of(":pk", PLAYER_PK)).build();
			EntityQueryResult<List<MatchTable>> properDynamoQuery = dynamoDbTemplate.query(MatchTable.class,
					dynamoDBQueryRequest, DynamoDbPageRequest.of(20));

			assertEquals(20, properDynamoQuery.getCount());

			var firstKey = properDynamoQuery.getLastEvaluatedKey();
			properDynamoQuery = dynamoDbTemplate.query(MatchTable.class, dynamoDBQueryRequest,
					DynamoDbPageRequest.of(20, properDynamoQuery.getLastEvaluatedKey()));
			assertNotEquals(firstKey.get("sortKey"), properDynamoQuery.getLastEvaluatedKey().get("sortKey"));
		}
	}
}
