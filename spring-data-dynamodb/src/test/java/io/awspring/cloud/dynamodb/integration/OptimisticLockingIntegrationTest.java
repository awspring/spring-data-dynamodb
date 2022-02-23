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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.awspring.cloud.dynamodb.LocalStackTestContainer;
import io.awspring.cloud.dynamodb.config.AbstractDynamoDbConfiguration;
import io.awspring.cloud.dynamodb.core.DynamoDbTemplate;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.annotation.Version;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

public class OptimisticLockingIntegrationTest extends LocalStackTestContainer {

	private static final String TABLE_NAME = "optimistic_locking_test";

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
				.credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
						.create(software.amazon.awssdk.auth.credentials.AwsBasicCredentials
								.create(localstack.getAccessKey(), localstack.getSecretKey())))
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

	@Test
	void newEntityGetsVersionZero() {
		VersionedEntity entity = new VersionedEntity("test-1", "initial data");
		assertThat(entity.getVersion()).isNull();

		template.save(entity);

		assertThat(entity.getVersion()).isEqualTo(0L);

		VersionedEntity fetched = template.findById("test-1", VersionedEntity.class);
		assertThat(fetched).isNotNull();
		assertThat(fetched.getVersion()).isEqualTo(0L);
		assertThat(fetched.getData()).isEqualTo("initial data");
	}

	@Test
	void saveIncrementsVersion() {
		VersionedEntity entity = new VersionedEntity("test-2", "initial data");
		template.save(entity);
		assertThat(entity.getVersion()).isEqualTo(0L);

		entity.setData("updated data");
		template.save(entity);

		assertThat(entity.getVersion()).isEqualTo(1L);

		VersionedEntity fetched = template.findById("test-2", VersionedEntity.class);
		assertThat(fetched.getVersion()).isEqualTo(1L);
		assertThat(fetched.getData()).isEqualTo("updated data");
	}

	@Test
	void updateIncrementsVersion() {
		VersionedEntity entity = new VersionedEntity("test-3", "initial data");
		template.save(entity);
		assertThat(entity.getVersion()).isEqualTo(0L);

		entity.setData("updated via update");
		template.update(entity);

		assertThat(entity.getVersion()).isEqualTo(1L);

		VersionedEntity fetched = template.findById("test-3", VersionedEntity.class);
		assertThat(fetched.getVersion()).isEqualTo(1L);
		assertThat(fetched.getData()).isEqualTo("updated via update");
	}

	@Test
	void concurrentSaveIsRejected() {
		VersionedEntity entity = new VersionedEntity("test-4", "initial data");
		template.save(entity);
		assertThat(entity.getVersion()).isEqualTo(0L);

		VersionedEntity staleEntity = template.findById("test-4", VersionedEntity.class);
		assertThat(staleEntity.getVersion()).isEqualTo(0L);

		entity.setData("fresh update");
		template.save(entity);
		assertThat(entity.getVersion()).isEqualTo(1L);

		staleEntity.setData("stale update");
		assertThatThrownBy(() -> template.save(staleEntity)).isInstanceOf(OptimisticLockingFailureException.class)
				.hasMessageContaining("Version mismatch");

		VersionedEntity fetched = template.findById("test-4", VersionedEntity.class);
		assertThat(fetched.getData()).isEqualTo("fresh update");
		assertThat(fetched.getVersion()).isEqualTo(1L);
	}

	@Test
	void concurrentUpdateIsRejected() {
		VersionedEntity entity = new VersionedEntity("test-5", "initial data");
		template.save(entity);

		VersionedEntity staleEntity = template.findById("test-5", VersionedEntity.class);

		entity.setData("fresh update");
		template.update(entity);
		assertThat(entity.getVersion()).isEqualTo(1L);

		staleEntity.setData("stale update");
		assertThatThrownBy(() -> template.update(staleEntity)).isInstanceOf(OptimisticLockingFailureException.class)
				.hasMessageContaining("Version mismatch");

		VersionedEntity fetched = template.findById("test-5", VersionedEntity.class);
		assertThat(fetched.getData()).isEqualTo("fresh update");
		assertThat(fetched.getVersion()).isEqualTo(1L);
	}

	@Test
	void multipleUpdatesIncrementVersionSequentially() {
		VersionedEntity entity = new VersionedEntity("test-6", "v0");
		template.save(entity);
		assertThat(entity.getVersion()).isEqualTo(0L);

		entity.setData("v1");
		template.update(entity);
		assertThat(entity.getVersion()).isEqualTo(1L);

		entity.setData("v2");
		template.update(entity);
		assertThat(entity.getVersion()).isEqualTo(2L);

		entity.setData("v3");
		template.update(entity);
		assertThat(entity.getVersion()).isEqualTo(3L);

		VersionedEntity fetched = template.findById("test-6", VersionedEntity.class);
		assertThat(fetched.getVersion()).isEqualTo(3L);
		assertThat(fetched.getData()).isEqualTo("v3");
	}
}
