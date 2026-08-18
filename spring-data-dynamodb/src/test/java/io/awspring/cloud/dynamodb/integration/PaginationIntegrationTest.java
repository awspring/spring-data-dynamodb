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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.LocalStackTestContainer;
import io.awspring.cloud.dynamodb.config.AbstractDynamoDbConfiguration;
import io.awspring.cloud.dynamodb.core.DynamoDbTemplate;
import io.awspring.cloud.dynamodb.core.EntityQueryResult;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.DynamoDbCompositeId;
import io.awspring.cloud.dynamodb.repository.DynamoDbRepository;
import io.awspring.cloud.dynamodb.repository.config.EnableDynamoDbRepositories;
import io.awspring.cloud.dynamodb.request.DynamoDbScanRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
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

public class PaginationIntegrationTest extends LocalStackTestContainer {

	private static final String TABLE_NAME = "pagination_it";
	private static final String PARTITION = "P";
	private static final int PAGE_SIZE = 2;

	private AnnotationConfigApplicationContext context;
	private PagedItemRepository repository;
	private DynamoDbTemplate template;

	@Table(tableName = TABLE_NAME)
	public static class PagedItem {

		@PartitionKey
		private String pk;

		@SortKey
		private Long sk;

		private String name;

		public PagedItem() {
		}

		public PagedItem(String pk, Long sk, String name) {
			this.pk = pk;
			this.sk = sk;
			this.name = name;
		}

		public String getPk() {
			return pk;
		}

		public void setPk(String pk) {
			this.pk = pk;
		}

		public Long getSk() {
			return sk;
		}

		public void setSk(Long sk) {
			this.sk = sk;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public interface PagedItemRepository extends DynamoDbRepository<PagedItem, DynamoDbCompositeId> {

		Window<PagedItem> findByPk(String pk, ScrollPosition position, Limit limit);
	}

	@EnableDynamoDbRepositories(basePackageClasses = PaginationIntegrationTest.class, considerNestedRepositories = true, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.awspring\\.cloud\\.dynamodb\\.integration\\.(?!PaginationIntegrationTest\\$).*"))
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
		DynamoDbClient dynamoDbClient = DynamoDbClient.builder().region(Region.of(localstack.getRegion()))
				.endpointOverride(localstack.getEndpoint())
				.credentialsProvider(StaticCredentialsProvider
						.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
				.build();

		recreateTable(dynamoDbClient);

		context = new AnnotationConfigApplicationContext();
		context.registerBean(DynamoDbClient.class, () -> dynamoDbClient);
		context.register(TestConfig.class);
		context.refresh();
		repository = context.getBean(PagedItemRepository.class);
		template = context.getBean(DynamoDbTemplate.class);
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.close();
		}
	}

	private static void recreateTable(DynamoDbClient client) {
		try {
			client.deleteTable(builder -> builder.tableName(TABLE_NAME));
		}
		catch (ResourceNotFoundException notFound) {
		}
		client.createTable(CreateTableRequest.builder().tableName(TABLE_NAME)
				.attributeDefinitions(
						AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
						AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.N).build())
				.keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
						KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
				.provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build());
	}

	private void insertItems(String pk, long... sortKeys) {
		for (long sk : sortKeys) {
			repository.save(new PagedItem(pk, sk, "item-" + sk));
		}
	}

	@Nested
	@DisplayName("Window/keyset pagination")
	class WindowPagination {

		@Test
		@DisplayName("keyset pagination over a numeric sort key returns every page in order")
		void findByPk_multiplePages_returnsAllInOrder() {
			insertItems(PARTITION, 1L, 2L, 3L, 4L, 5L);

			List<Long> collected = new ArrayList<>();
			ScrollPosition position = ScrollPosition.keyset();
			int pages = 0;
			Window<PagedItem> window;

			do {
				window = repository.findByPk(PARTITION, position, Limit.of(PAGE_SIZE));
				window.forEach(item -> collected.add(item.getSk()));
				pages++;
				if (window.hasNext()) {
					position = window.positionAt(window.getContent().size() - 1);
				}
			}
			while (window.hasNext() && pages < 10);

			int totalPages = pages;
			assertAll("pagination traverses all rows exactly once",
					() -> assertEquals(List.of(1L, 2L, 3L, 4L, 5L), collected,
							"every row must be returned exactly once, in ascending numeric sort-key order"),
					() -> assertEquals(3, totalPages, "5 rows at page size 2 must span 3 pages (2 + 2 + 1)"));
		}

		@Test
		@DisplayName("resume cursor holds a domain Number, never an AttributeValue")
		void findByPk_firstPage_cursorIsCleanDomainType() {
			insertItems(PARTITION, 10L, 20L, 30L);

			Window<PagedItem> firstPage = repository.findByPk(PARTITION, ScrollPosition.keyset(), Limit.of(1));

			assertTrue(firstPage.hasNext(), "with 3 rows at page size 1 there must be a next page");
			ScrollPosition next = firstPage.positionAt(firstPage.getContent().size() - 1);
			KeysetScrollPosition keyset = assertInstanceOf(KeysetScrollPosition.class, next,
					"DynamoDB pagination must produce a keyset ScrollPosition");

			Object skCursor = keyset.getKeys().get("sk");
			assertAll("cursor value is a clean domain type",
					() -> assertNotNull(skCursor, "the resume cursor must carry the numeric sort key"),
					() -> assertInstanceOf(Number.class, skCursor,
							"the cursor value must be a clean domain Number -- never an AWS SDK AttributeValue"),
					() -> assertFalse(skCursor.getClass().getName().startsWith("software.amazon.awssdk"),
							"no AWS SDK type may leak into the pagination cursor"));
		}
	}

	@Nested
	@DisplayName("Scan pagination")
	class ScanPagination {

		@Test
		@DisplayName("scan returns LastEvaluatedKey when more pages remain")
		void scan_withLimit_returnsLastEvaluatedKey() {
			insertItems(PARTITION, 1L, 2L, 3L);

			EntityQueryResult<List<PagedItem>> firstPage = template.scan(PagedItem.class,
					DynamoDbScanRequest.Builder.builder().withLimit(1).build());

			Map<String, Object> cursor = firstPage.getLastEvaluatedKey();
			assertAll("scan cursor present",
					() -> assertNotNull(cursor, "scan() must return a LastEvaluatedKey when more pages remain"),
					() -> assertFalse(cursor.isEmpty(), "the scan cursor must not be empty when more pages remain"));
		}

		@Test
		@DisplayName("count aggregates across every scan page")
		void count_withSmallPageSize_aggregatesAllPages() {
			insertItems(PARTITION, 1L, 2L, 3L, 4L, 5L);

			long count = template.count(PagedItem.class, DynamoDbScanRequest.Builder.builder().withLimit(2).build());

			assertEquals(5L, count, "count must aggregate across every scan page, not just the first");
		}

		@Test
		@DisplayName("exists finds a match that lives beyond the first scan page")
		void exists_matchOnLaterPage_returnsTrue() {
			insertItems(PARTITION, 1L, 2L, 3L, 4L, 5L);
			repository.save(new PagedItem(PARTITION, 6L, "needle"));

			DynamoDbScanRequest request = DynamoDbScanRequest.Builder.builder().withLimit(1)
					.withFilterExpression("#n = :n").withExpressionAttributeNames(Map.of("#n", "name"))
					.withExpressionAttributeValues(Map.of(":n", "needle")).build();

			assertTrue(template.exists(PagedItem.class, request),
					"exists must find a match on a later scan page, not only the first");
		}

		@Test
		@DisplayName("findAll returns every row via auto-paginated scan")
		void findAll_allRows_returnsAll() {
			insertItems(PARTITION, 1L, 2L, 3L, 4L, 5L);

			List<PagedItem> all = template.findAll(PagedItem.class);

			assertEquals(5, all.size(), "findAll must return every row via an auto-paginated scan");
		}
	}
}
