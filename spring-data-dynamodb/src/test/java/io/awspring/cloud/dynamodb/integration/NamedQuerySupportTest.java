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
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.AllowScan;
import io.awspring.cloud.dynamodb.repository.DynamoDbRepository;
import io.awspring.cloud.dynamodb.repository.config.EnableDynamoDbRepositories;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

public class NamedQuerySupportTest extends LocalStackTestContainer {

	private static final String TABLE_NAME = "named_query_test_entity";

	private AnnotationConfigApplicationContext context;
	private NamedQueryTestRepository repository;

	@Table(tableName = TABLE_NAME)
	public static class NamedQueryTestEntity {

		@PartitionKey
		private String id;

		private String round;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getRound() {
			return round;
		}

		public void setRound(String round) {
			this.round = round;
		}
	}

	public interface NamedQueryTestRepository extends DynamoDbRepository<NamedQueryTestEntity, String> {

		@AllowScan
		List<NamedQueryTestEntity> findByNamedQuery(String round);
	}

	@EnableDynamoDbRepositories(basePackageClasses = NamedQuerySupportTest.class, considerNestedRepositories = true, namedQueriesLocation = "classpath:dynamodb-named-queries-test.properties")
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
				.credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
						.create(software.amazon.awssdk.auth.credentials.AwsBasicCredentials
								.create(localstack.getAccessKey(), localstack.getSecretKey())))
				.build();

		try {
			dynamoDbClient.deleteTable(builder -> builder.tableName(TABLE_NAME));
		}
		catch (ResourceNotFoundException notFound) {
		}

		dynamoDbClient.createTable(CreateTableRequest.builder().tableName(TABLE_NAME)
				.attributeDefinitions(
						AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build())
				.keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
				.provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build());

		context = new AnnotationConfigApplicationContext();
		context.registerBean(DynamoDbClient.class, () -> dynamoDbClient);
		context.register(TestConfig.class);
		context.refresh();
		repository = context.getBean(NamedQueryTestRepository.class);
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.close();
		}
	}

	private static NamedQueryTestEntity entityWithIdAndRound(String id, String round) {
		NamedQueryTestEntity entity = new NamedQueryTestEntity();
		entity.setId(id);
		entity.setRound(round);
		return entity;
	}

	@Test
	void namedQueryResolvesAndExecutesAsFilterExpressionScan() {
		repository.save(entityWithIdAndRound("nq-1", "active"));
		repository.save(entityWithIdAndRound("nq-2", "inactive"));
		repository.save(entityWithIdAndRound("nq-3", "active"));

		List<NamedQueryTestEntity> active = repository.findByNamedQuery("active");

		Assertions.assertEquals(2, active.size(), "Named query should find 2 entities with round=active");
		Assertions.assertTrue(active.stream().allMatch(e -> "active".equals(e.getRound())),
				"All returned entities should have round=active");
	}

	@Test
	void namedQueryTakesPrecedenceOverDerivedQuery() {

		repository.save(entityWithIdAndRound("nq-precedence-1", "test"));

		List<NamedQueryTestEntity> results = repository.findByNamedQuery("test");

		Assertions.assertEquals(1, results.size(), "Named query should resolve and execute, not PartTree derivation");
	}
}
