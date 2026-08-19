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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.LocalStackTestContainer;
import io.awspring.spring.data.dynamodb.config.AbstractDynamoDbConfiguration;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.repository.DynamoDbCompositeId;
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepository;
import io.awspring.spring.data.dynamodb.repository.config.EnableDynamoDbRepositories;
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

public class CompositeIdRepositoryIntegrationTest extends LocalStackTestContainer {

	private static final String SCALAR_TABLE = "composite_it_scalar";
	private static final String COMPOSITE_TABLE = "composite_it_composite";
	private static final String SCALAR_ID = "scalar-1";
	private static final String SCALAR_PAYLOAD = "hello";
	private static final String COMPOSITE_PK = "cust-1";
	private static final String MATCH_SK_1 = "MATCH#1";
	private static final String MATCH_SK_2 = "MATCH#2";

	private AnnotationConfigApplicationContext context;
	private ScalarEntityRepository scalarRepository;
	private CompositeEntityRepository compositeRepository;

	@Table(tableName = SCALAR_TABLE)
	public static class ScalarEntity {

		@PartitionKey
		private String id;

		private String payload;

		public ScalarEntity() {
		}

		public ScalarEntity(String id, String payload) {
			this.id = id;
			this.payload = payload;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getPayload() {
			return payload;
		}

		public void setPayload(String payload) {
			this.payload = payload;
		}
	}

	public interface ScalarEntityRepository extends DynamoDbRepository<ScalarEntity, String> {
	}

	@Table(tableName = COMPOSITE_TABLE)
	public static class CompositeEntity {

		@PartitionKey
		private String partitionKey;

		@SortKey
		private String sortKey;

		private String payload;

		public CompositeEntity() {
		}

		public CompositeEntity(String partitionKey, String sortKey, String payload) {
			this.partitionKey = partitionKey;
			this.sortKey = sortKey;
			this.payload = payload;
		}

		public String getPartitionKey() {
			return partitionKey;
		}

		public void setPartitionKey(String partitionKey) {
			this.partitionKey = partitionKey;
		}

		public String getSortKey() {
			return sortKey;
		}

		public void setSortKey(String sortKey) {
			this.sortKey = sortKey;
		}

		public String getPayload() {
			return payload;
		}

		public void setPayload(String payload) {
			this.payload = payload;
		}
	}

	public interface CompositeEntityRepository extends DynamoDbRepository<CompositeEntity, DynamoDbCompositeId> {
	}

	@EnableDynamoDbRepositories(basePackageClasses = CompositeIdRepositoryIntegrationTest.class, considerNestedRepositories = true, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.awspring\\.cloud\\.dynamodb\\.integration\\.(?!CompositeIdRepositoryIntegrationTest\\$).*"))
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

		recreateScalarTable(dynamoDbClient);
		recreateCompositeTable(dynamoDbClient);

		context = new AnnotationConfigApplicationContext();
		context.registerBean(DynamoDbClient.class, () -> dynamoDbClient);
		context.register(TestConfig.class);
		context.refresh();
		scalarRepository = context.getBean(ScalarEntityRepository.class);
		compositeRepository = context.getBean(CompositeEntityRepository.class);
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.close();
		}
	}

	private static void recreateScalarTable(DynamoDbClient client) {
		try {
			client.deleteTable(builder -> builder.tableName(SCALAR_TABLE));
		}
		catch (ResourceNotFoundException notFound) {
		}
		client.createTable(CreateTableRequest.builder().tableName(SCALAR_TABLE)
				.attributeDefinitions(
						AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build())
				.keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
				.provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build());
	}

	private static void recreateCompositeTable(DynamoDbClient client) {
		try {
			client.deleteTable(builder -> builder.tableName(COMPOSITE_TABLE));
		}
		catch (ResourceNotFoundException notFound) {
		}
		client.createTable(CreateTableRequest.builder().tableName(COMPOSITE_TABLE).attributeDefinitions(
				AttributeDefinition.builder().attributeName("partitionKey").attributeType(ScalarAttributeType.S)
						.build(),
				AttributeDefinition.builder().attributeName("sortKey").attributeType(ScalarAttributeType.S).build())
				.keySchema(KeySchemaElement.builder().attributeName("partitionKey").keyType(KeyType.HASH).build(),
						KeySchemaElement.builder().attributeName("sortKey").keyType(KeyType.RANGE).build())
				.provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build());
	}

	@Nested
	@DisplayName("Scalar key operations (partition-key only)")
	class ScalarKeyOperations {

		@Test
		@DisplayName("findById returns the entity for a plain partition key")
		void findById_existingEntity_returnsIt() {
			scalarRepository.save(new ScalarEntity(SCALAR_ID, SCALAR_PAYLOAD));

			Optional<ScalarEntity> found = scalarRepository.findById(SCALAR_ID);

			assertTrue(found.isPresent(), "findById(scalar) must resolve a partition-key-only entity");
			assertAll("returned entity matches", () -> assertEquals(SCALAR_ID, found.get().getId()),
					() -> assertEquals(SCALAR_PAYLOAD, found.get().getPayload()));
		}

		@Test
		@DisplayName("existsById reflects presence for a plain partition key")
		void existsById_presentAndAbsent_correctBooleans() {
			scalarRepository.save(new ScalarEntity("scalar-exists", "x"));

			assertAll("existence checks for scalar key", () -> assertTrue(scalarRepository.existsById("scalar-exists")),
					() -> assertFalse(scalarRepository.existsById("scalar-absent")));
		}

		@Test
		@DisplayName("deleteById removes the entity for a plain partition key")
		void deleteById_existingEntity_removesIt() {
			scalarRepository.save(new ScalarEntity("scalar-delete", "x"));
			assertTrue(scalarRepository.existsById("scalar-delete"));

			scalarRepository.deleteById("scalar-delete");

			assertFalse(scalarRepository.existsById("scalar-delete"),
					"deleteById(scalar) must remove a partition-key-only entity");
		}
	}

	@Nested
	@DisplayName("Composite key operations (partition + sort key)")
	class CompositeKeyOperations {

		@Test
		@DisplayName("findById resolves the exact partition+sort key item")
		void findById_compositKey_resolvesExactItem() {
			compositeRepository.save(new CompositeEntity(COMPOSITE_PK, MATCH_SK_1, "first"));
			compositeRepository.save(new CompositeEntity(COMPOSITE_PK, MATCH_SK_2, "second"));

			Optional<CompositeEntity> found = compositeRepository
					.findById(DynamoDbCompositeId.of(COMPOSITE_PK, MATCH_SK_2));

			assertTrue(found.isPresent(), "findById(composite) must resolve the PK+SK item");
			assertAll("correct item selected by composite key",
					() -> assertEquals(COMPOSITE_PK, found.get().getPartitionKey()),
					() -> assertEquals(MATCH_SK_2, found.get().getSortKey()),
					() -> assertEquals("second", found.get().getPayload(),
							"the sort key must select MATCH#2, not the sibling MATCH#1 under the same partition key"));
		}

		@Test
		@DisplayName("existsById reflects presence of the exact PK+SK pair")
		void existsById_compositeKey_correctBooleans() {
			compositeRepository.save(new CompositeEntity(COMPOSITE_PK, MATCH_SK_1, "first"));

			assertAll("existence checks for composite key",
					() -> assertTrue(compositeRepository.existsById(DynamoDbCompositeId.of(COMPOSITE_PK, MATCH_SK_1))),
					() -> assertFalse(compositeRepository.existsById(DynamoDbCompositeId.of(COMPOSITE_PK, "MATCH#99"))),
					() -> assertFalse(
							compositeRepository.existsById(DynamoDbCompositeId.of("cust-absent", MATCH_SK_1))));
		}

		@Test
		@DisplayName("deleteById removes only the targeted PK+SK pair, leaving siblings intact")
		void deleteById_compositeKey_removeOnlyTarget() {
			compositeRepository.save(new CompositeEntity(COMPOSITE_PK, MATCH_SK_1, "first"));
			compositeRepository.save(new CompositeEntity(COMPOSITE_PK, MATCH_SK_2, "second"));

			compositeRepository.deleteById(DynamoDbCompositeId.of(COMPOSITE_PK, MATCH_SK_1));

			assertAll("only the targeted item is removed",
					() -> assertFalse(compositeRepository.existsById(DynamoDbCompositeId.of(COMPOSITE_PK, MATCH_SK_1)),
							"deleteById(composite) must remove the targeted PK+SK item"),
					() -> assertTrue(compositeRepository.existsById(DynamoDbCompositeId.of(COMPOSITE_PK, MATCH_SK_2)),
							"the sibling row under the same partition key must be left intact"));
		}
	}
}
