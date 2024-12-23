package io.awspring.cloud.v3.dynamodb;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.v3.dynamodb.core.DynamoDbTemplate;
import io.awspring.cloud.v3.dynamodb.core.EntityQueryResult;
import io.awspring.cloud.v3.dynamodb.core.EntityReadResult;
import io.awspring.cloud.v3.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.v3.dynamodb.core.mapping.events.DynamoDbBeforeSaveCallback;
import io.awspring.cloud.v3.dynamodb.entities.Company;
import io.awspring.cloud.v3.dynamodb.entities.CompanySingleTable;
import io.awspring.cloud.v3.dynamodb.entities.Person;
import io.awspring.cloud.v3.dynamodb.entities.shop.*;
import io.awspring.cloud.v3.dynamodb.request.DynamoDbPageRequest;
import io.awspring.cloud.v3.dynamodb.request.DynamoDbQueryRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.callback.EntityCallbacks;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;


public class DynamoDbTemplateTest extends LocalStackTestContainer {

    private DynamoDbTemplate dynamoDbTemplate;
    private DynamoDbClient dynamoDbClient;
    private MappingDynamoDbConverter mappingDynamoDbConverter;

    {
        DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
        ObjectMapper obj = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        obj.findAndRegisterModules();
        mappingDynamoDbConverter = new MappingDynamoDbConverter(mappingContext, obj);
        mappingDynamoDbConverter.afterPropertiesSet();
        dynamoDbClient = DynamoDbClient.builder().region(Region.of(localstack.getRegion()))
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB)).build();
        EntityCallbacks callbacks = EntityCallbacks.create();
        callbacks.addEntityCallback((DynamoDbBeforeSaveCallback<Object>) (entity, tableName) -> {
            Assertions.assertNotNull(tableName);
            return entity;
        });
        dynamoDbTemplate = new DynamoDbTemplate(dynamoDbClient, mappingDynamoDbConverter);
        dynamoDbTemplate.setEntityCallbacks(callbacks);

        KeySchemaElement idKey = KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build();
        AttributeDefinition id = AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build();
        CreateTableRequest createTableRequest = CreateTableRequest.builder().tableName("someTableName").attributeDefinitions(id).keySchema(idKey).provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build()).build();
        dynamoDbClient.createTable(createTableRequest);
        dynamoDbClient.createTable(CreateTableRequest.builder().tableName("myPojo").attributeDefinitions(id).keySchema(idKey).provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build()).build());


        //Create Table
        idKey = KeySchemaElement.builder().attributeName("partitionKey").keyType(KeyType.HASH).build();
        id = AttributeDefinition.builder().attributeName("partitionKey").attributeType(ScalarAttributeType.S).build();
        KeySchemaElement sortKey = KeySchemaElement.builder().attributeName("sortKey").keyType(KeyType.RANGE).build();
        AttributeDefinition sortDef = AttributeDefinition.builder().attributeName("sortKey").attributeType(ScalarAttributeType.S).build();
        createTableRequest = CreateTableRequest.builder().tableName("shop").attributeDefinitions(id, sortDef).keySchema(idKey, sortKey).provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build()).build();
        dynamoDbClient.createTable(createTableRequest);
    }


    @Test
    void insertShouldInsertEntity() {
        LocalDate testDate = LocalDate.now();
        MappingDynamoDbConverterTest.TestClass testClassToBeInserted = new MappingDynamoDbConverterTest.TestClass("testID", testDate);

        dynamoDbTemplate.save(testClassToBeInserted);

        Map keyToFetch = new HashMap();
        keyToFetch.put("id", AttributeValue.builder().s("testID").build());
        Map<String, AttributeValue> attributeValueHashMap = dynamoDbClient.getItem(GetItemRequest.builder().key(keyToFetch).tableName("someTableName").build()).item();

        Assertions.assertEquals(attributeValueHashMap.get("id").s(), testClassToBeInserted.getId());
        Assertions.assertEquals(LocalDate.parse(attributeValueHashMap.get("value").s()), testClassToBeInserted.getValue());
    }

    @Test
    void insertShouldInsertCompanySingleTable() {
        KeySchemaElement idKey = KeySchemaElement.builder().attributeName("partitionKey").keyType(KeyType.HASH).build();
        AttributeDefinition id = AttributeDefinition.builder().attributeName("partitionKey").attributeType(ScalarAttributeType.S).build();
        KeySchemaElement sortKey = KeySchemaElement.builder().attributeName("sortKey").keyType(KeyType.RANGE).build();
        AttributeDefinition sortDef = AttributeDefinition.builder().attributeName("sortKey").attributeType(ScalarAttributeType.S).build();
        CreateTableRequest createTableRequest = CreateTableRequest.builder().tableName("company_table").attributeDefinitions(id, sortDef).keySchema(idKey, sortKey).provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build()).build();
        dynamoDbClient.createTable(createTableRequest);

        Company company = new Company("Amazon Web Service", "AWS", "AWS101");
        Person person = new Person("Jeff", "Amazon", LocalDate.now(), "AWS101");
        CompanySingleTable companySingleTable = new CompanySingleTable("test", "sort", company, null);
        CompanySingleTable companySingleTable2 = new CompanySingleTable("test2", "sort2", null, person);

        dynamoDbTemplate.save(companySingleTable);
        dynamoDbTemplate.save(companySingleTable2);

        Map keyToFetch = new HashMap();
        keyToFetch.put("partitionKey", AttributeValue.builder().s("test").build());
        keyToFetch.put("sortKey", AttributeValue.builder().s("sort").build());
        Map<String, AttributeValue> attributeValueHashMap = dynamoDbClient.getItem(GetItemRequest.builder().key(keyToFetch).tableName("company_table").build()).item();


        Assertions.assertEquals(attributeValueHashMap.get("partitionKey").s(), companySingleTable.getPartitionKey());
        Assertions.assertEquals(attributeValueHashMap.get("sortKey").s(), companySingleTable.getSortKey());


        CompanySingleTable toCompare = dynamoDbTemplate.findEntityByKeys("test2", "sort2", CompanySingleTable.class);
        keyToFetch.put("partitionKey", AttributeValue.builder().s("test2").build());
        keyToFetch.put("sortKey", AttributeValue.builder().s("sort2").build());
        attributeValueHashMap = dynamoDbClient.getItem(GetItemRequest.builder().key(keyToFetch).tableName("company_table").build()).item();


        Assertions.assertEquals(toCompare.getPerson().getFirstName(), companySingleTable2.getPerson().getFirstName());
        Assertions.assertEquals(attributeValueHashMap.get("name"), null);
    }


    @Test
    void insertShouldInsertShopSingleTable() {

        //Prepare Objects 1 Order 1 Account on same Address
        Address address = new Address("Zagreb", 10000L, "Trg bana Josipa Jelacica", "Hrvatska");
        LocalDate date = LocalDate.now();
        OrderSK order = new OrderSK("myUser", UUID.randomUUID(), Status.SHIPPED, date, address, "SOME#123512512");
        PersonInformation personInformation = new PersonInformation("myUser", "Josip Jelacic", "fake_email", date, address);
        ShopTable shopTable1 = new ShopTable("USER#myUser", "USER#myUser", null, personInformation);
        ShopTable shopTable2 = new ShopTable("USER#myUser", "ORDER#15236", order, null);

        //Save Order and Account
        dynamoDbTemplate.save(shopTable1);
        dynamoDbTemplate.save(shopTable2);

        // Read all accounts and Order with 1 Query
        EntityReadResult<List<ShopTable>> sqlTypeQueryResult = dynamoDbTemplate.executeStatement("Select * from shop", null, ShopTable.class);

        // Read with proper way
        DynamoDbQueryRequest dynamoDBQueryRequest = DynamoDbQueryRequest.Builder.request().withKeyConditionExpression("partitionKey = :pk").withExpressionAttributeValues(Map.of(":pk", "USER#myUser")).build();
        EntityQueryResult<List<ShopTable>> properDynamoQuery = dynamoDbTemplate.query(ShopTable.class,  dynamoDBQueryRequest, null);

        EntityQueryResult<List<ShopTable>> secondQuery = dynamoDbTemplate.query(ShopTable.class,
                (DynamoDbQueryRequest.Builder db) -> db.withKeyConditionExpression("partitionKey = :pk")
                        .withExpressionAttributeValues(Map.of(":pk", "USER#myUser")).build(), null);

        //Read Specific Order
        ShopTable shopTable = dynamoDbTemplate.findEntityByKeys("USER#myUser", "ORDER#15236", ShopTable.class);

        Assertions.assertIterableEquals(List.of(shopTable1, shopTable2), properDynamoQuery.getEntity());
        Assertions.assertIterableEquals(List.of(shopTable2, shopTable1), sqlTypeQueryResult.getEntity());
        Assertions.assertEquals(shopTable, shopTable2);
        Assertions.assertEquals(properDynamoQuery.getEntity().size(), secondQuery.getEntity().size());

        dynamoDbTemplate.delete(shopTable1);
        dynamoDbTemplate.delete(ShopTable.class, shopTable2.getPartitionKey(), shopTable2.getSortKey());

        sqlTypeQueryResult = dynamoDbTemplate.executeStatement("Select * from shop", null, ShopTable.class);
        Assertions.assertEquals(sqlTypeQueryResult.getEntity().size(), 0);
    }




    @Test
    void insertShouldInsertEntityNullFields() {
        MappingDynamoDbConverterTest.TestClass testClassToBeInserted = new MappingDynamoDbConverterTest.TestClass("anotherId", null);

        dynamoDbTemplate.save(testClassToBeInserted);

        Map keyToFetch = new HashMap();
        keyToFetch.put("id", AttributeValue.builder().s("anotherId").build());
        Map<String, AttributeValue> attributeValueHashMap = dynamoDbClient.getItem(GetItemRequest.builder().key(keyToFetch).tableName("someTableName").build()).item();

        Assertions.assertEquals(attributeValueHashMap.get("id").s(), testClassToBeInserted.getId());
        Assertions.assertTrue(attributeValueHashMap.get("value").nul());
    }


    @Test
    void insertThenDelete() {
        LocalDate testDate = LocalDate.now();
        MappingDynamoDbConverterTest.TestClass testClassToBeInserted = new MappingDynamoDbConverterTest.TestClass("testID2", testDate);

        dynamoDbTemplate.save(testClassToBeInserted);

        Map keyToFetch = new HashMap();
        keyToFetch.put("id", AttributeValue.builder().s("testID2").build());
        Map<String, AttributeValue> attributeValueHashMap = dynamoDbClient.getItem(GetItemRequest.builder().key(keyToFetch).tableName("someTableName").build()).item();

        Assertions.assertEquals(attributeValueHashMap.get("id").s(), testClassToBeInserted.getId());
        Assertions.assertEquals(LocalDate.parse(attributeValueHashMap.get("value").s()), testClassToBeInserted.getValue());

        dynamoDbTemplate.delete(testClassToBeInserted);

        attributeValueHashMap = dynamoDbClient.getItem(GetItemRequest.builder().key(keyToFetch).tableName("someTableName").build()).item();
        Assertions.assertEquals(attributeValueHashMap.size(), 0L);
    }


    @Test
    void insertThenGet() {
        LocalDate testDate = LocalDate.now();
        MappingDynamoDbConverterTest.TestClass testClassToBeInserted = new MappingDynamoDbConverterTest.TestClass("testID3", testDate, Arrays.asList("test1", "test2"), Arrays.asList(new MappingDynamoDbConverterTest.TelephoneNumber("099"), new MappingDynamoDbConverterTest.TelephoneNumber("095")));

        dynamoDbTemplate.save(testClassToBeInserted);
        MappingDynamoDbConverterTest.TestClass readClass = dynamoDbTemplate.getEntityByKey(testClassToBeInserted.getId(), MappingDynamoDbConverterTest.TestClass.class);

        Assertions.assertEquals(readClass.getId(), testClassToBeInserted.getId());
        Assertions.assertEquals(readClass.getValue(), testClassToBeInserted.getValue());
        Assertions.assertEquals(readClass.getMyList(), Arrays.asList("test1", "test2"));
        Assertions.assertEquals(readClass.getTelephoneNumber().size(), 2);
    }


    @Test
    void insertThenGet2() {
        Map<String, AttributeValue> attributeValueMap = new HashMap<>();
        attributeValueMap.put("id", AttributeValue.builder().s("2").build());
        attributeValueMap.put("telephoneNumber", AttributeValue.builder().s("some random phone number").build());
        attributeValueMap.put("bill", AttributeValue.builder().n(BigDecimal.valueOf(200).toString()).build());
        List<AttributeValue> listOfValues = new ArrayList<>();
        listOfValues.add(AttributeValue.builder().s("Jhon").build());
        listOfValues.add(AttributeValue.builder().s("Doe").build());
        attributeValueMap.put("ownerFacts", AttributeValue.builder().l(listOfValues).build());
        List<AttributeValue> listOfAnimals = new ArrayList<>();
        listOfAnimals.add(AttributeValue.builder().s("cat").build());
        listOfAnimals.add(AttributeValue.builder().s("doggo").build());
        Map<String, AttributeValue> mapOfList = new HashMap<>();
        mapOfList.put("myName", AttributeValue.builder().l(listOfValues).build());
        mapOfList.put("animals", AttributeValue.builder().l(listOfAnimals).build());
        attributeValueMap.put("ownerInformations", AttributeValue.builder().m(mapOfList).build());
        dynamoDbClient.putItem(PutItemRequest.builder().item(attributeValueMap).tableName("myPojo").build());

        MyPojo myPojo = dynamoDbTemplate.getEntityByKey("2", MyPojo.class);
        System.out.println();
    }


    @Test
    void insertUpdateThenGet() {
        LocalDate testDate = LocalDate.now();
        MappingDynamoDbConverterTest.TestClass testClassToBeInserted = new MappingDynamoDbConverterTest.TestClass("testID4", testDate);

        dynamoDbTemplate.save(testClassToBeInserted);

        LocalDate newDate = testDate.plusDays(1);
        testClassToBeInserted.setValue(newDate);
        testClassToBeInserted.getMyList().add("new");
        dynamoDbTemplate.update(testClassToBeInserted);

        Map keyToFetch = new HashMap();
        keyToFetch.put("id", AttributeValue.builder().s("testID4").build());
        Map<String, AttributeValue> attributeValueHashMap = dynamoDbClient.getItem(GetItemRequest.builder().key(keyToFetch).tableName("someTableName").build()).item();


        Assertions.assertEquals(attributeValueHashMap.get("id").s(), testClassToBeInserted.getId());
        Assertions.assertEquals(LocalDate.parse(attributeValueHashMap.get("value").s()), newDate);
        Assertions.assertEquals(attributeValueHashMap.get("myList").l().size(), testClassToBeInserted.getMyList().size());
    }


    @Test
    void insertAndThenExecute() {
        LocalDate testDate = LocalDate.now();
        MappingDynamoDbConverterTest.TestClass testClassToBeInserted = new MappingDynamoDbConverterTest.TestClass("randomId", testDate);

        dynamoDbTemplate.save(testClassToBeInserted);

        EntityReadResult<List<MappingDynamoDbConverterTest.TestClass>> list = dynamoDbTemplate.executeStatement("Select * from someTableName", null, MappingDynamoDbConverterTest.TestClass.class);
        Assertions.assertEquals(list.getEntity().size(), 1L);
        Assertions.assertEquals(list.getEntity().get(0).getId(), testClassToBeInserted.getId());
        Assertions.assertEquals(list.getEntity().get(0).getMyList(), testClassToBeInserted.getMyList());
        Assertions.assertEquals(list.getEntity().get(0).getTelephoneNumber(), testClassToBeInserted.getTelephoneNumber());
        Assertions.assertEquals(list.getEntity().get(0).getValue(), testClassToBeInserted.getValue());
    }


    @Test
    void insertBatchTest() {
        LocalDate testDate = LocalDate.now();
        List<MappingDynamoDbConverterTest.TestClass> arrayList = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            arrayList.add(new MappingDynamoDbConverterTest.TestClass("randomId" + i, testDate));
            if (i % 20 == 0) {
                dynamoDbTemplate.saveAll(arrayList, MappingDynamoDbConverterTest.TestClass.class);
                arrayList = new ArrayList<>();
            }
        }

        EntityReadResult<List<MappingDynamoDbConverterTest.TestClass>> list = dynamoDbTemplate.executeStatement("Select * from someTableName", null, MappingDynamoDbConverterTest.TestClass.class);

        Assertions.assertEquals(list.getEntity().size(), 101);
    }

    @Test
    void testPagination() {
        OrderSK order = new OrderSK("myUser", UUID.randomUUID(), Status.SHIPPED, null, null, "SOME#123512512");
        List<ShopTable> entities = new ArrayList<>();
        for(int i =1; i <= 100; i++) {
            if (i % 20 ==  0 ) {
                dynamoDbTemplate.saveAll(entities, ShopTable.class);
                entities.clear();
            }
            var shopEntity = new ShopTable("USER#myUser", "ORDER#" + i, order, null);
            entities.add(shopEntity);
        }

        // Read only first 20 entities
        DynamoDbQueryRequest dynamoDBQueryRequest = DynamoDbQueryRequest.Builder.request().withKeyConditionExpression("partitionKey = :pk").withExpressionAttributeValues(Map.of(":pk", "USER#myUser")).build();
        EntityQueryResult<List<ShopTable>> properDynamoQuery = dynamoDbTemplate.query(ShopTable.class,  dynamoDBQueryRequest, DynamoDbPageRequest.of(20));

        Assertions.assertEquals(properDynamoQuery.
                getCount(), 20);
        var key = properDynamoQuery.getLastEvaluatedKey();
        properDynamoQuery = dynamoDbTemplate.query(ShopTable.class,  dynamoDBQueryRequest, DynamoDbPageRequest.of(20, properDynamoQuery.getLastEvaluatedKey()));
        Assertions.assertNotEquals(key.get("sortKey"), properDynamoQuery.getLastEvaluatedKey().get("sortKey"));
    }


}
