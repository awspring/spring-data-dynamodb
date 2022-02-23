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
import io.awspring.cloud.dynamodb.core.DynamoDbTemplate;
import io.awspring.cloud.dynamodb.core.EntityQueryResult;
import io.awspring.cloud.dynamodb.core.EntityReadResult;
import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.events.DynamoDbBeforeSaveCallback;
import io.awspring.cloud.dynamodb.entities.ArenaRow;
import io.awspring.cloud.dynamodb.entities.Player;
import io.awspring.cloud.dynamodb.entities.PlayerCard;
import io.awspring.cloud.dynamodb.entities.Team;
import io.awspring.cloud.dynamodb.entities.arena.*;
import io.awspring.cloud.dynamodb.entities.arena.MatchSK;
import io.awspring.cloud.dynamodb.entities.arena.MatchStatus;
import io.awspring.cloud.dynamodb.entities.arena.MatchTable;
import io.awspring.cloud.dynamodb.entities.arena.PlayerContact;
import io.awspring.cloud.dynamodb.entities.arena.Venue;
import io.awspring.cloud.dynamodb.mapping.PlayerCardEntity;
import io.awspring.cloud.dynamodb.request.DynamoDbPageRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbQueryRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.callback.EntityCallbacks;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

public class DynamoDbTemplateTest extends LocalStackTestContainer {

	private DynamoDbTemplate dynamoDbTemplate;
	private DynamoDbClient dynamoDbClient;
	private MappingDynamoDbConverter mappingDynamoDbConverter;

	{
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingDynamoDbConverter = new MappingDynamoDbConverter(mappingContext);
		mappingDynamoDbConverter.afterPropertiesSet();
		dynamoDbClient = DynamoDbClient.builder().region(Region.of(localstack.getRegion()))
				.endpointOverride(localstack.getEndpoint())
				.credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
						.create(software.amazon.awssdk.auth.credentials.AwsBasicCredentials
								.create(localstack.getAccessKey(), localstack.getSecretKey())))
				.build();
		EntityCallbacks callbacks = EntityCallbacks.create();
		callbacks.addEntityCallback((DynamoDbBeforeSaveCallback<Object>) (entity, tableName) -> {
			Assertions.assertNotNull(tableName);
			return entity;
		});
		dynamoDbTemplate = new DynamoDbTemplate(dynamoDbClient, mappingDynamoDbConverter);
		dynamoDbTemplate.setEntityCallbacks(callbacks);

		KeySchemaElement idKey = KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build();
		AttributeDefinition id = AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S)
				.build();
		CreateTableRequest createTableRequest = CreateTableRequest.builder().tableName("playerCardEntity")
				.attributeDefinitions(id).keySchema(idKey).provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build();
		createTableIfAbsent(createTableRequest);
		createTableIfAbsent(
				CreateTableRequest.builder().tableName("playerCard").attributeDefinitions(id).keySchema(idKey)
						.provisionedThroughput(
								ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
						.build());

		idKey = KeySchemaElement.builder().attributeName("partitionKey").keyType(KeyType.HASH).build();
		id = AttributeDefinition.builder().attributeName("partitionKey").attributeType(ScalarAttributeType.S).build();
		KeySchemaElement sortKey = KeySchemaElement.builder().attributeName("sortKey").keyType(KeyType.RANGE).build();
		AttributeDefinition sortDef = AttributeDefinition.builder().attributeName("sortKey")
				.attributeType(ScalarAttributeType.S).build();
		createTableRequest = CreateTableRequest.builder().tableName("arena").attributeDefinitions(id, sortDef)
				.keySchema(idKey, sortKey).provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build();
		createTableIfAbsent(createTableRequest);
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

	@Test
	void insertShouldInsertEntity() {
		LocalDate testDate = LocalDate.now();
		PlayerCardEntity playerCardEntity = new PlayerCardEntity("testID", testDate);

		dynamoDbTemplate.save(playerCardEntity);

		Map keyToFetch = new HashMap();
		keyToFetch.put("id", AttributeValue.builder().s("testID").build());
		Map<String, AttributeValue> attributeValueHashMap = dynamoDbClient
				.getItem(GetItemRequest.builder().key(keyToFetch).tableName("playerCardEntity").build()).item();

		Assertions.assertEquals(attributeValueHashMap.get("id").s(), playerCardEntity.getId());
		Assertions.assertEquals(LocalDate.parse(attributeValueHashMap.get("registeredOn").s()),
				playerCardEntity.getRegisteredOn());
	}

	@Test
	void insertShouldInsertArenaRow() {
		KeySchemaElement idKey = KeySchemaElement.builder().attributeName("partitionKey").keyType(KeyType.HASH).build();
		AttributeDefinition id = AttributeDefinition.builder().attributeName("partitionKey")
				.attributeType(ScalarAttributeType.S).build();
		KeySchemaElement sortKey = KeySchemaElement.builder().attributeName("sortKey").keyType(KeyType.RANGE).build();
		AttributeDefinition sortDef = AttributeDefinition.builder().attributeName("sortKey")
				.attributeType(ScalarAttributeType.S).build();
		CreateTableRequest createTableRequest = CreateTableRequest.builder().tableName("arena_table")
				.attributeDefinitions(id, sortDef).keySchema(idKey, sortKey).provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build();
		dynamoDbClient.createTable(createTableRequest);

		Team team = new Team("Cloud Esports", "CLE", "NA");
		Player player = new Player("Faker", "Lee Sang-hyeok", LocalDate.now(), "KR");
		ArenaRow arenaRow = new ArenaRow("test", "sort", team, null);
		ArenaRow arenaRow2 = new ArenaRow("test2", "sort2", null, player);

		dynamoDbTemplate.save(arenaRow);
		dynamoDbTemplate.save(arenaRow2);

		Map keyToFetch = new HashMap();
		keyToFetch.put("partitionKey", AttributeValue.builder().s("test").build());
		keyToFetch.put("sortKey", AttributeValue.builder().s("sort").build());
		Map<String, AttributeValue> attributeValueHashMap = dynamoDbClient
				.getItem(GetItemRequest.builder().key(keyToFetch).tableName("arena_table").build()).item();

		Assertions.assertEquals(attributeValueHashMap.get("partitionKey").s(), arenaRow.getPartitionKey());
		Assertions.assertEquals(attributeValueHashMap.get("sortKey").s(), arenaRow.getSortKey());

		ArenaRow toCompare = dynamoDbTemplate.findById("test2", "sort2", ArenaRow.class);
		keyToFetch.put("partitionKey", AttributeValue.builder().s("test2").build());
		keyToFetch.put("sortKey", AttributeValue.builder().s("sort2").build());
		attributeValueHashMap = dynamoDbClient
				.getItem(GetItemRequest.builder().key(keyToFetch).tableName("arena_table").build()).item();

		Assertions.assertEquals(toCompare.getPlayer().getGamerTag(), arenaRow2.getPlayer().getGamerTag());
		Assertions.assertEquals(attributeValueHashMap.get("name"), null);
	}

	@Test
	void insertShouldInsertShopSingleTable() {

		Venue venue = new Venue("Zagreb", 10000L, "Trg bana Josipa Jelacica", "Hrvatska");
		LocalDate date = LocalDate.now();
		MatchSK match = new MatchSK("myUser", UUID.randomUUID(), MatchStatus.COMPLETED, date, venue, "SOME#123512512");
		PlayerContact playerContact = new PlayerContact("myUser", "Josip Jelacic", "fake_email", date, venue);
		MatchTable shopTable1 = new MatchTable("PLAYER#myUser", "PLAYER#myUser", null, playerContact);
		MatchTable shopTable2 = new MatchTable("PLAYER#myUser", "MATCH#15236", match, null);

		dynamoDbTemplate.save(shopTable1);
		dynamoDbTemplate.save(shopTable2);

		EntityReadResult<List<MatchTable>> sqlTypeQueryResult = dynamoDbTemplate.executeStatement("Select * from arena",
				null, MatchTable.class);

		DynamoDbQueryRequest dynamoDBQueryRequest = DynamoDbQueryRequest.Builder.request()
				.withKeyConditionExpression("partitionKey = :pk")
				.withExpressionAttributeValues(Map.of(":pk", "PLAYER#myUser")).build();
		EntityQueryResult<List<MatchTable>> properDynamoQuery = dynamoDbTemplate.query(MatchTable.class,
				dynamoDBQueryRequest, null);

		EntityQueryResult<List<MatchTable>> secondQuery = dynamoDbTemplate.query(MatchTable.class,
				(DynamoDbQueryRequest.Builder db) -> db.withKeyConditionExpression("partitionKey = :pk")
						.withExpressionAttributeValues(Map.of(":pk", "PLAYER#myUser")).build(),
				null);

		MatchTable matchTable = dynamoDbTemplate.findById("PLAYER#myUser", "MATCH#15236", MatchTable.class);

		Assertions.assertIterableEquals(List.of(shopTable1, shopTable2), properDynamoQuery.getEntity());
		Assertions.assertIterableEquals(List.of(shopTable2, shopTable1), sqlTypeQueryResult.getEntity());
		Assertions.assertEquals(matchTable, shopTable2);
		Assertions.assertEquals(properDynamoQuery.getEntity().size(), secondQuery.getEntity().size());

		Map<String, AttributeValue> rawKey = new HashMap<>();
		rawKey.put("partitionKey", AttributeValue.builder().s("PLAYER#myUser").build());
		rawKey.put("sortKey", AttributeValue.builder().s("MATCH#15236").build());
		Map<String, AttributeValue> rawOrderItem = dynamoDbClient
				.getItem(GetItemRequest.builder().key(rawKey).tableName("arena").build()).item();
		Assertions.assertEquals("myUser", rawOrderItem.get("tournamentId").s());
		Assertions.assertNotNull(rawOrderItem.get("matchId"));
		Assertions.assertNotNull(rawOrderItem.get("GLOBAL_SK_1"));
		Assertions.assertFalse(rawOrderItem.containsKey("match"));
		Assertions.assertFalse(rawOrderItem.containsKey("playerContact"));

		dynamoDbTemplate.delete(shopTable1);
		dynamoDbTemplate.delete(MatchTable.class, shopTable2.getPartitionKey(), shopTable2.getSortKey());

		sqlTypeQueryResult = dynamoDbTemplate.executeStatement("Select * from arena", null, MatchTable.class);
		Assertions.assertEquals(sqlTypeQueryResult.getEntity().size(), 0);
	}

	@Test
	void insertShouldInsertEntityNullFields() {
		PlayerCardEntity playerCardEntity = new PlayerCardEntity("anotherId", null);

		dynamoDbTemplate.save(playerCardEntity);

		Map keyToFetch = new HashMap();
		keyToFetch.put("id", AttributeValue.builder().s("anotherId").build());
		Map<String, AttributeValue> attributeValueHashMap = dynamoDbClient
				.getItem(GetItemRequest.builder().key(keyToFetch).tableName("playerCardEntity").build()).item();

		Assertions.assertEquals(attributeValueHashMap.get("id").s(), playerCardEntity.getId());
		Assertions.assertTrue(attributeValueHashMap.get("registeredOn").nul());
	}

	@Test
	void insertThenDelete() {
		LocalDate testDate = LocalDate.now();
		PlayerCardEntity playerCardEntity = new PlayerCardEntity("testID2", testDate);

		dynamoDbTemplate.save(playerCardEntity);

		Map keyToFetch = new HashMap();
		keyToFetch.put("id", AttributeValue.builder().s("testID2").build());
		Map<String, AttributeValue> attributeValueHashMap = dynamoDbClient
				.getItem(GetItemRequest.builder().key(keyToFetch).tableName("playerCardEntity").build()).item();

		Assertions.assertEquals(attributeValueHashMap.get("id").s(), playerCardEntity.getId());
		Assertions.assertEquals(LocalDate.parse(attributeValueHashMap.get("registeredOn").s()),
				playerCardEntity.getRegisteredOn());

		dynamoDbTemplate.delete(playerCardEntity);

		attributeValueHashMap = dynamoDbClient
				.getItem(GetItemRequest.builder().key(keyToFetch).tableName("playerCardEntity").build()).item();
		Assertions.assertEquals(attributeValueHashMap.size(), 0L);
	}

	@Test
	void insertThenGet() {
		LocalDate testDate = LocalDate.now();
		PlayerCardEntity playerCardEntity = new PlayerCardEntity("testID3", testDate, Arrays.asList("test1", "test2"),
				Arrays.asList("099", "095"));

		dynamoDbTemplate.save(playerCardEntity);
		PlayerCardEntity readClass = dynamoDbTemplate.findById(playerCardEntity.getId(), PlayerCardEntity.class);

		Assertions.assertEquals(readClass.getId(), playerCardEntity.getId());
		Assertions.assertEquals(readClass.getRegisteredOn(), playerCardEntity.getRegisteredOn());
		Assertions.assertEquals(readClass.getTags(), Arrays.asList("test1", "test2"));
		Assertions.assertEquals(readClass.getAliases().size(), 2);
	}

	@Test
	void insertThenGet2() {
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
		dynamoDbClient.putItem(PutItemRequest.builder().item(attributeValueMap).tableName("playerCard").build());

		PlayerCard playerCard = dynamoDbTemplate.findById("2", PlayerCard.class);

		Assertions.assertEquals("2", playerCard.getId());
		Assertions.assertEquals("some random phone number", playerCard.profile().getContactHandle());
		Assertions.assertEquals(200, playerCard.profile().points);
		Assertions.assertEquals("cat", playerCard.profile().statsByGame.get("animals").get(0));
		Assertions.assertEquals("doggo", playerCard.profile().statsByGame.get("animals").get(1));
		Assertions.assertEquals("Jhon", playerCard.profile().achievements.get(0));

	}

	@Test
	void insertUpdateThenGet() {
		LocalDate testDate = LocalDate.now();
		PlayerCardEntity playerCardEntity = new PlayerCardEntity("testID4", testDate);

		dynamoDbTemplate.save(playerCardEntity);

		LocalDate newDate = testDate.plusDays(1);
		playerCardEntity.setRegisteredOn(newDate);
		playerCardEntity.getTags().add("new");
		dynamoDbTemplate.update(playerCardEntity);

		Map keyToFetch = new HashMap();
		keyToFetch.put("id", AttributeValue.builder().s("testID4").build());
		Map<String, AttributeValue> attributeValueHashMap = dynamoDbClient
				.getItem(GetItemRequest.builder().key(keyToFetch).tableName("playerCardEntity").build()).item();

		Assertions.assertEquals(attributeValueHashMap.get("id").s(), playerCardEntity.getId());
		Assertions.assertEquals(LocalDate.parse(attributeValueHashMap.get("registeredOn").s()), newDate);
		Assertions.assertEquals(attributeValueHashMap.get("tags").l().size(), playerCardEntity.getTags().size());
	}

	@Test
	void insertAndThenExecute() {
		clearTable("playerCardEntity", "id");
		LocalDate testDate = LocalDate.now();
		PlayerCardEntity playerCardEntity = new PlayerCardEntity("randomId", testDate);

		dynamoDbTemplate.save(playerCardEntity);

		EntityReadResult<List<PlayerCardEntity>> list = dynamoDbTemplate
				.executeStatement("Select * from playerCardEntity", null, PlayerCardEntity.class);
		Assertions.assertEquals(list.getEntity().size(), 1L);
		Assertions.assertEquals(list.getEntity().get(0).getId(), playerCardEntity.getId());
		Assertions.assertEquals(list.getEntity().get(0).getTags(), playerCardEntity.getTags());
		Assertions.assertEquals(list.getEntity().get(0).getAliases(), playerCardEntity.getAliases());
		Assertions.assertEquals(list.getEntity().get(0).getRegisteredOn(), playerCardEntity.getRegisteredOn());
	}

	@Test
	void insertBatchTest() {
		clearTable("playerCardEntity", "id");
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

		Assertions.assertEquals(list.getEntity().size(), 101);
	}

	@Test
	void testPagination() {
		MatchSK match = new MatchSK("myUser", UUID.randomUUID(), MatchStatus.COMPLETED, null, null, "SOME#123512512");
		List<MatchTable> entities = new ArrayList<>();
		for (int i = 1; i <= 100; i++) {
			if (i % 20 == 0) {
				dynamoDbTemplate.saveAll(entities);
				entities.clear();
			}
			var shopEntity = new MatchTable("PLAYER#myUser", "MATCH#" + i, match, null);
			entities.add(shopEntity);
		}

		DynamoDbQueryRequest dynamoDBQueryRequest = DynamoDbQueryRequest.Builder.request()
				.withKeyConditionExpression("partitionKey = :pk")
				.withExpressionAttributeValues(Map.of(":pk", "PLAYER#myUser")).build();
		EntityQueryResult<List<MatchTable>> properDynamoQuery = dynamoDbTemplate.query(MatchTable.class,
				dynamoDBQueryRequest, DynamoDbPageRequest.of(20));

		Assertions.assertEquals(properDynamoQuery.getCount(), 20);
		var key = properDynamoQuery.getLastEvaluatedKey();
		properDynamoQuery = dynamoDbTemplate.query(MatchTable.class, dynamoDBQueryRequest,
				DynamoDbPageRequest.of(20, properDynamoQuery.getLastEvaluatedKey()));
		Assertions.assertNotEquals(key.get("sortKey"), properDynamoQuery.getLastEvaluatedKey().get("sortKey"));
	}

}
