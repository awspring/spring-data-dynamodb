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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.LocalStackTestContainer;
import io.awspring.cloud.dynamodb.config.AbstractDynamoDbConfiguration;
import io.awspring.cloud.dynamodb.core.mapping.AggregateItem;
import io.awspring.cloud.dynamodb.core.mapping.AggregateTable;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.AggregateRepository;
import io.awspring.cloud.dynamodb.repository.DynamoDbRepository;
import io.awspring.cloud.dynamodb.repository.ExpressionName;
import io.awspring.cloud.dynamodb.repository.Query;
import io.awspring.cloud.dynamodb.repository.config.EnableDynamoDbRepositories;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.repository.query.Param;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

public class AggregateRepositoryIntegrationTest extends LocalStackTestContainer {

	private static final String TABLE_NAME = "commerce";
	private static final String CUSTOMER_PK = "CUSTOMER#1";
	private static final String NONEXISTENT_PK = "CUSTOMER#NONEXISTENT";
	private static final String ORDER_SK = "ORDER#9876";
	private static final String ORDER_STATUS = "PLACED";
	private static final String ITEM_PREFIX = "ITEM#";
	private static final int EXPECTED_ITEM_COUNT = 3;

	private DynamoDbClient dynamoDbClient;
	private AnnotationConfigApplicationContext context;
	private OrderRepository orderRepository;
	private OrderItemRepository orderItemRepository;
	private OrderAggregateRepository aggregateRepository;

	@Table(tableName = TABLE_NAME)
	public static class Order {
		@PartitionKey
		private String pk;
		@SortKey
		private String sk;
		private String status;

		public Order() {
		}

		public String getPk() {
			return pk;
		}

		public void setPk(String pk) {
			this.pk = pk;
		}

		public String getSk() {
			return sk;
		}

		public void setSk(String sk) {
			this.sk = sk;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		static Order create(String pk, String sk, String status) {
			Order o = new Order();
			o.pk = pk;
			o.sk = sk;
			o.status = status;
			return o;
		}
	}

	@Table(tableName = TABLE_NAME)
	public static class OrderItem {
		@PartitionKey
		private String pk;
		@SortKey
		private String sk;
		private String sku;
		private int quantity;

		public OrderItem() {
		}

		public String getPk() {
			return pk;
		}

		public void setPk(String pk) {
			this.pk = pk;
		}

		public String getSk() {
			return sk;
		}

		public void setSk(String sk) {
			this.sk = sk;
		}

		public String getSku() {
			return sku;
		}

		public void setSku(String sku) {
			this.sku = sku;
		}

		public int getQuantity() {
			return quantity;
		}

		public void setQuantity(int quantity) {
			this.quantity = quantity;
		}

		static OrderItem create(String pk, String sk, String sku, int quantity) {
			OrderItem i = new OrderItem();
			i.pk = pk;
			i.sk = sk;
			i.sku = sku;
			i.quantity = quantity;
			return i;
		}
	}

	@AggregateTable(tableName = TABLE_NAME, partitionKey = "pk", sortKey = "sk")
	public static class OrderAggregate {
		@AggregateItem(startsWith = "ORDER#")
		private Order order;
		@AggregateItem(startsWith = "ITEM#")
		private List<OrderItem> items;

		public OrderAggregate() {
		}

		public Order getOrder() {
			return order;
		}

		public List<OrderItem> getItems() {
			return items;
		}
	}

	public interface OrderRepository extends DynamoDbRepository<Order, String> {
	}

	public interface OrderItemRepository extends DynamoDbRepository<OrderItem, String> {
	}

	public interface OrderAggregateRepository extends AggregateRepository<OrderAggregate> {

		@Query(keyConditionExpression = "#pk = :pk AND begins_with(#sk, :prefix)", names = {
				@ExpressionName(name = "#pk", value = "pk"), @ExpressionName(name = "#sk", value = "sk") })
		Optional<OrderAggregate> loadWithPrefix(@Param("pk") String pk, @Param("prefix") String prefix);
	}

	@EnableDynamoDbRepositories(basePackageClasses = AggregateRepositoryIntegrationTest.class, considerNestedRepositories = true, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.awspring\\.cloud\\.dynamodb\\.integration\\.(?!AggregateRepositoryIntegrationTest\\$).*"))
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

	@BeforeEach
	void setUp() {
		dynamoDbClient = DynamoDbClient.builder().region(Region.of(localstack.getRegion()))
				.endpointOverride(localstack.getEndpoint())
				.credentialsProvider(StaticCredentialsProvider
						.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
				.build();

		recreateTable();

		org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300))
				.until(() -> "ACTIVE".equals(
						dynamoDbClient.describeTable(b -> b.tableName(TABLE_NAME)).table().tableStatusAsString()));

		context = new AnnotationConfigApplicationContext();
		context.registerBean(DynamoDbClient.class, () -> dynamoDbClient);
		context.register(TestConfig.class);
		context.refresh();
		orderRepository = context.getBean(OrderRepository.class);
		orderItemRepository = context.getBean(OrderItemRepository.class);
		aggregateRepository = context.getBean(OrderAggregateRepository.class);
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.close();
		}
	}

	private void recreateTable() {
		try {
			dynamoDbClient.deleteTable(builder -> builder.tableName(TABLE_NAME));
		}
		catch (ResourceNotFoundException ignored) {
		}

		dynamoDbClient.createTable(CreateTableRequest.builder().tableName(TABLE_NAME)
				.attributeDefinitions(
						AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
						AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
				.keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
						KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
				.provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build());
	}

	private void seedPartition() {
		orderRepository.save(Order.create(CUSTOMER_PK, ORDER_SK, ORDER_STATUS));
		orderItemRepository.save(OrderItem.create(CUSTOMER_PK, "ITEM#a", "WIDGET-1", 2));
		orderItemRepository.save(OrderItem.create(CUSTOMER_PK, "ITEM#b", "WIDGET-2", 1));
		orderItemRepository.save(OrderItem.create(CUSTOMER_PK, "ITEM#c", "GADGET-3", 5));
	}

	@Nested
	@DisplayName("Partition-level queries")
	class PartitionQuery {

		@Test
		@DisplayName("findByPartitionKey folds the entire partition into a typed aggregate")
		void findByPartitionKey_withHeterogeneousRows_foldsIntoAggregate() {
			seedPartition();

			Optional<OrderAggregate> result = aggregateRepository.findByPartitionKey(CUSTOMER_PK);

			assertTrue(result.isPresent());
			OrderAggregate aggregate = result.get();
			assertAll("aggregate contains the order and all items",
					() -> assertNotNull(aggregate.getOrder(), "order slot must be populated"),
					() -> assertEquals(ORDER_STATUS, aggregate.getOrder().getStatus()),
					() -> assertNotNull(aggregate.getItems(), "items list must be populated"),
					() -> assertEquals(EXPECTED_ITEM_COUNT, aggregate.getItems().size()));
		}
	}

	@Nested
	@DisplayName("Point reads")
	class PointRead {

		@Test
		@DisplayName("findByPartitionKeyAndSortKey returns only the exact row")
		void findByPartitionKeyAndSortKey_exactMatch_returnsOnlyOrderRow() {
			seedPartition();

			Optional<OrderAggregate> result = aggregateRepository.findByPartitionKeyAndSortKey(CUSTOMER_PK, ORDER_SK);

			assertTrue(result.isPresent());
			OrderAggregate aggregate = result.get();
			assertAll("point read returns only the ORDER row", () -> assertNotNull(aggregate.getOrder()),
					() -> assertEquals(ORDER_STATUS, aggregate.getOrder().getStatus()),
					() -> assertTrue(aggregate.getItems() == null || aggregate.getItems().isEmpty(),
							"point read on ORDER# should not include ITEM# rows"));
		}
	}

	@Nested
	@DisplayName("Prefix queries")
	class PrefixQuery {

		@Test
		@DisplayName("findByPartitionKeyAndSortKeyStartingWith filters to the given prefix")
		void findByPkAndSkStartingWith_itemPrefix_returnsOnlyItems() {
			seedPartition();

			Optional<OrderAggregate> result = aggregateRepository.findByPartitionKeyAndSortKeyStartingWith(CUSTOMER_PK,
					ITEM_PREFIX);

			assertTrue(result.isPresent());
			OrderAggregate aggregate = result.get();
			assertAll("prefix query returns only ITEM# rows",
					() -> assertNull(aggregate.getOrder(), "ORDER# row should not be included in ITEM# prefix query"),
					() -> assertEquals(EXPECTED_ITEM_COUNT, aggregate.getItems().size()));
		}
	}

	@Nested
	@DisplayName("Existence checks")
	class Existence {

		@Test
		@DisplayName("existsByPartitionKey returns true for populated and false for missing")
		void existsByPartitionKey_populatedAndMissing_correctBooleans() {
			seedPartition();

			assertAll("existence checks", () -> assertTrue(aggregateRepository.existsByPartitionKey(CUSTOMER_PK)),
					() -> assertFalse(aggregateRepository.existsByPartitionKey(NONEXISTENT_PK)));
		}
	}

	@Nested
	@DisplayName("@Query annotation support")
	class AnnotationQuery {

		@Test
		@DisplayName("@Query passthrough folds items into the aggregate")
		void queryAnnotation_withItemPrefix_foldsAggregate() {
			seedPartition();

			Optional<OrderAggregate> result = aggregateRepository.loadWithPrefix(CUSTOMER_PK, ITEM_PREFIX);

			assertTrue(result.isPresent());
			OrderAggregate aggregate = result.get();
			assertAll("@Query returns only ITEM# rows", () -> assertNull(aggregate.getOrder()),
					() -> assertEquals(EXPECTED_ITEM_COUNT, aggregate.getItems().size()));
		}
	}
}
