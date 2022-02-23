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
package io.awspring.cloud.dynamodb.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.request.DynamoDbConditionRequest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.annotation.Version;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

class OptimisticLockingTest {

	private DynamoDbClient mockClient;
	private DynamoDbTemplate template;
	private MappingDynamoDbConverter converter;

	@Table(tableName = "versioned_entity")
	static class VersionedEntity {
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

	@BeforeEach
	void setUp() {
		mockClient = mock(DynamoDbClient.class);
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		template = new DynamoDbTemplate(mockClient, converter);
	}

	@Test
	void saveNewEntityInitializesVersionToZero() {
		VersionedEntity entity = new VersionedEntity("id1", "data1");
		assertThat(entity.getVersion()).isNull();

		when(mockClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

		template.save(entity);

		assertThat(entity.getVersion()).isEqualTo(0L);

		ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
		verify(mockClient).putItem(captor.capture());

		PutItemRequest request = captor.getValue();
		assertThat(request.item()).containsKey("version");
		assertThat(request.item().get("version").n()).isEqualTo("0");
		assertThat(request.conditionExpression()).contains("attribute_not_exists(#__version)");
		assertThat(request.expressionAttributeNames()).containsEntry("#__version", "version");
	}

	@Test
	void saveExistingEntityIncrementsVersion() {
		VersionedEntity entity = new VersionedEntity("id1", "data1");
		entity.setVersion(5L);

		when(mockClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

		template.save(entity);

		assertThat(entity.getVersion()).isEqualTo(6L);

		ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
		verify(mockClient).putItem(captor.capture());

		PutItemRequest request = captor.getValue();
		assertThat(request.item()).containsKey("version");
		assertThat(request.item().get("version").n()).isEqualTo("6");
		assertThat(request.conditionExpression()).contains("#__version = :__prevVersion");
		assertThat(request.expressionAttributeNames()).containsEntry("#__version", "version");
		assertThat(request.expressionAttributeValues()).containsKey(":__prevVersion");
		assertThat(request.expressionAttributeValues().get(":__prevVersion").n()).isEqualTo("5");
	}

	@Test
	void saveExistingEntityWithStaleVersionThrowsOptimisticLockingException() {
		VersionedEntity entity = new VersionedEntity("id1", "data1");
		entity.setVersion(5L);

		when(mockClient.putItem(any(PutItemRequest.class)))
				.thenThrow(ConditionalCheckFailedException.builder().message("Conditional check failed").build());

		assertThatThrownBy(() -> template.save(entity)).isInstanceOf(OptimisticLockingFailureException.class)
				.hasMessageContaining("Version mismatch").hasMessageContaining("expected version: 5");
	}

	@Test
	void updateEntityIncrementsVersion() {
		VersionedEntity entity = new VersionedEntity("id1", "data1");
		entity.setVersion(3L);

		Map<String, AttributeValue> returnedAttributes = new HashMap<>();
		returnedAttributes.put("id", AttributeValue.builder().s("id1").build());
		returnedAttributes.put("data", AttributeValue.builder().s("data1").build());
		returnedAttributes.put("version", AttributeValue.builder().n("4").build());

		when(mockClient.updateItem(any(UpdateItemRequest.class)))
				.thenReturn(UpdateItemResponse.builder().attributes(returnedAttributes).build());

		template.update(entity);

		assertThat(entity.getVersion()).isEqualTo(4L);

		ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
		verify(mockClient).updateItem(captor.capture());

		UpdateItemRequest request = captor.getValue();
		assertThat(request.conditionExpression()).contains("#__version = :__prevVersion");
		assertThat(request.expressionAttributeNames()).containsEntry("#__version", "version");
		assertThat(request.expressionAttributeValues()).containsKey(":__prevVersion");
		assertThat(request.expressionAttributeValues().get(":__prevVersion").n()).isEqualTo("3");
	}

	@Test
	void updateEntityWithStaleVersionThrowsOptimisticLockingException() {
		VersionedEntity entity = new VersionedEntity("id1", "data1");
		entity.setVersion(3L);

		when(mockClient.updateItem(any(UpdateItemRequest.class)))
				.thenThrow(ConditionalCheckFailedException.builder().message("Conditional check failed").build());

		assertThatThrownBy(() -> template.update(entity)).isInstanceOf(OptimisticLockingFailureException.class)
				.hasMessageContaining("Version mismatch").hasMessageContaining("expected version: 3");
	}

	@Test
	void saveWithUserConditionCombinesWithVersionCondition() {
		VersionedEntity entity = new VersionedEntity("id1", "data1");

		Map<String, String> userNames = new HashMap<>();
		userNames.put("#data", "data");
		Map<String, Object> userValues = new HashMap<>();
		userValues.put(":val", "someValue");

		var conditionRequest = DynamoDbConditionRequest.Builder.request().withConditionExpression("#data = :val")
				.withExpressionAttributeNames(userNames).withExpressionAttributeValues(userValues).build();

		when(mockClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

		template.save(entity, conditionRequest);

		ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
		verify(mockClient).putItem(captor.capture());

		PutItemRequest request = captor.getValue();
		assertThat(request.conditionExpression()).contains("#data = :val").contains("AND")
				.contains("attribute_not_exists(#__version)");
		assertThat(request.expressionAttributeNames()).containsEntry("#data", "data").containsEntry("#__version",
				"version");
		assertThat(request.expressionAttributeValues()).containsKey(":val");
	}

	@Table(tableName = "non_versioned_entity")
	static class NonVersionedEntity {
		@PartitionKey
		private String id;
		private String data;

		public NonVersionedEntity() {
		}

		public NonVersionedEntity(String id, String data) {
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
	}

	@Test
	void saveNonVersionedEntityWorksWithoutVersionCondition() {
		NonVersionedEntity entity = new NonVersionedEntity("id1", "data1");

		when(mockClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

		template.save(entity);

		ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
		verify(mockClient).putItem(captor.capture());

		PutItemRequest request = captor.getValue();
		assertThat(request.item()).doesNotContainKey("version");
		assertThat(request.conditionExpression()).isNullOrEmpty();
	}

	@Test
	void updateNonVersionedEntityWorksWithoutVersionCondition() {
		NonVersionedEntity entity = new NonVersionedEntity("id1", "data1");

		Map<String, AttributeValue> returnedAttributes = new HashMap<>();
		returnedAttributes.put("id", AttributeValue.builder().s("id1").build());
		returnedAttributes.put("data", AttributeValue.builder().s("data1").build());

		when(mockClient.updateItem(any(UpdateItemRequest.class)))
				.thenReturn(UpdateItemResponse.builder().attributes(returnedAttributes).build());

		template.update(entity);

		ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
		verify(mockClient).updateItem(captor.capture());

		UpdateItemRequest request = captor.getValue();
		assertThat(request.conditionExpression()).isNullOrEmpty();
	}
}
