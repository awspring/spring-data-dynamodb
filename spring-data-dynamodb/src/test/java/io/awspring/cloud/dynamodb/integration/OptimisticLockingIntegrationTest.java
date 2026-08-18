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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.LocalStackTestContainer;
import io.awspring.cloud.dynamodb.config.AbstractDynamoDbConfiguration;
import io.awspring.cloud.dynamodb.core.DynamoDbTemplate;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.annotation.Version;
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

public class OptimisticLockingIntegrationTest extends LocalStackTestContainer {

	private static final String TABLE_NAME = "optimistic_locking_test";
	private static final String INITIAL_DATA = "initial data";
	private static final String UPDATED_DATA = "updated data";
	private static final String FRESH_UPDATE = "fresh update";
	private static final String STALE_UPDATE = "stale update";

	private AnnotationConfigApplicationContext context;
	private DynamoDbTemplate template;
	private DynamoDbClient dynamoDbClient;

	@Table(tableName = TABLE_NAME)
	public static class VersionedEntity {
		@PartitionKey
		private String id;

		private String data;

		@Version
		private Long version;

		public VersionedEntity() {
		}

		public VersionedEntity(String id, String data) {
			this.id = id;
			this.data = data;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getData() {
			return data;
		}

		public void setData(String data) {
			this.data = data;
		}

		public Long getVersion() {
			return version;
		}

		public void setVersion(Long version) {
			this.version = version;
		}
	}

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

		context = new AnnotationConfigApplicationContext();
		context.registerBean(DynamoDbClient.class, () -> dynamoDbClient);
		context.register(TestConfig.class);
		context.refresh();

		template = context.getBean(DynamoDbTemplate.class);
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
		catch (ResourceNotFoundException notFound) {
		}

		dynamoDbClient.createTable(CreateTableRequest.builder().tableName(TABLE_NAME)
				.attributeDefinitions(
						AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build())
				.keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
				.provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build());
	}

	private VersionedEntity saveAndReturn(String id, String data) {
		VersionedEntity entity = new VersionedEntity(id, data);
		template.save(entity);
		return entity;
	}

	@Nested
	@DisplayName("Version assignment")
	class VersionAssignment {

		@Test
		@DisplayName("a new entity receives version 0 on first save")
		void newEntity_firstSave_receivesVersionZero() {
			VersionedEntity entity = new VersionedEntity("test-1", INITIAL_DATA);
			assertNull(entity.getVersion());

			template.save(entity);

			VersionedEntity fetched = template.findById("test-1", VersionedEntity.class);
			assertAll("version and data after first save", () -> assertEquals(0L, entity.getVersion()),
					() -> assertNotNull(fetched), () -> assertEquals(0L, fetched.getVersion()),
					() -> assertEquals(INITIAL_DATA, fetched.getData()));
		}

		@Test
		@DisplayName("multiple sequential updates increment the version monotonically")
		void multipleUpdates_incrementVersionSequentially() {
			VersionedEntity entity = saveAndReturn("test-6", "v0");
			assertEquals(0L, entity.getVersion());

			entity.setData("v1");
			template.update(entity);
			assertEquals(1L, entity.getVersion());

			entity.setData("v2");
			template.update(entity);
			assertEquals(2L, entity.getVersion());

			entity.setData("v3");
			template.update(entity);
			assertEquals(3L, entity.getVersion());

			VersionedEntity fetched = template.findById("test-6", VersionedEntity.class);
			assertAll("final state after three updates", () -> assertEquals(3L, fetched.getVersion()),
					() -> assertEquals("v3", fetched.getData()));
		}
	}

	@Nested
	@DisplayName("Save versioning")
	class SaveVersioning {

		@Test
		@DisplayName("save increments the version from 0 to 1")
		void save_afterInitialSave_incrementsVersion() {
			VersionedEntity entity = saveAndReturn("test-2", INITIAL_DATA);
			assertEquals(0L, entity.getVersion());

			entity.setData(UPDATED_DATA);
			template.save(entity);

			VersionedEntity fetched = template.findById("test-2", VersionedEntity.class);
			assertAll("version and data after second save", () -> assertEquals(1L, entity.getVersion()),
					() -> assertEquals(1L, fetched.getVersion()), () -> assertEquals(UPDATED_DATA, fetched.getData()));
		}
	}

	@Nested
	@DisplayName("Update versioning")
	class UpdateVersioning {

		@Test
		@DisplayName("update increments the version from 0 to 1")
		void update_afterInitialSave_incrementsVersion() {
			VersionedEntity entity = saveAndReturn("test-3", INITIAL_DATA);
			assertEquals(0L, entity.getVersion());

			entity.setData("updated via update");
			template.update(entity);

			VersionedEntity fetched = template.findById("test-3", VersionedEntity.class);
			assertAll("version and data after update", () -> assertEquals(1L, entity.getVersion()),
					() -> assertEquals(1L, fetched.getVersion()),
					() -> assertEquals("updated via update", fetched.getData()));
		}
	}

	@Nested
	@DisplayName("Conflict detection")
	class ConflictDetection {

		@Test
		@DisplayName("a save with a stale version is rejected with OptimisticLockingFailureException")
		void save_withStaleVersion_isRejected() {
			VersionedEntity entity = saveAndReturn("test-4", INITIAL_DATA);
			assertEquals(0L, entity.getVersion());

			VersionedEntity staleEntity = template.findById("test-4", VersionedEntity.class);
			assertEquals(0L, staleEntity.getVersion());

			entity.setData(FRESH_UPDATE);
			template.save(entity);
			assertEquals(1L, entity.getVersion());

			staleEntity.setData(STALE_UPDATE);
			OptimisticLockingFailureException ex = assertThrows(OptimisticLockingFailureException.class,
					() -> template.save(staleEntity));
			assertTrue(ex.getMessage().contains("Version mismatch"),
					"exception message should indicate version mismatch");

			VersionedEntity fetched = template.findById("test-4", VersionedEntity.class);
			assertAll("the fresh update must win", () -> assertEquals(FRESH_UPDATE, fetched.getData()),
					() -> assertEquals(1L, fetched.getVersion()));
		}

		@Test
		@DisplayName("an update with a stale version is rejected with OptimisticLockingFailureException")
		void update_withStaleVersion_isRejected() {
			VersionedEntity entity = saveAndReturn("test-5", INITIAL_DATA);

			VersionedEntity staleEntity = template.findById("test-5", VersionedEntity.class);

			entity.setData(FRESH_UPDATE);
			template.update(entity);
			assertEquals(1L, entity.getVersion());

			staleEntity.setData(STALE_UPDATE);
			OptimisticLockingFailureException ex = assertThrows(OptimisticLockingFailureException.class,
					() -> template.update(staleEntity));
			assertTrue(ex.getMessage().contains("Version mismatch"),
					"exception message should indicate version mismatch");

			VersionedEntity fetched = template.findById("test-5", VersionedEntity.class);
			assertAll("the fresh update must win", () -> assertEquals(FRESH_UPDATE, fetched.getData()),
					() -> assertEquals(1L, fetched.getVersion()));
		}
	}
}
