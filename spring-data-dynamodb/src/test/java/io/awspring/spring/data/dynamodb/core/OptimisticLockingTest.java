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
package io.awspring.spring.data.dynamodb.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.request.DynamoDbConditionRequest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.annotation.Version;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

class OptimisticLockingTest {

	private static final String ENTITY_ID = "id1";
	private static final String ENTITY_DATA = "data1";
	private static final String VERSION_ATTRIBUTE = "version";
	private static final String VERSION_PLACEHOLDER = "#__version";
	private static final String PREV_VERSION_PLACEHOLDER = ":__prevVersion";

	private DynamoDbClient mockClient;
	private DynamoDbTemplate template;

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

	@BeforeEach
	void setUp() {
		mockClient = mock(DynamoDbClient.class);
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		template = new DynamoDbTemplate(mockClient, converter);
	}

	@Nested
	@DisplayName("save — versioned entity")
	class SaveVersioned {

		@Test
		@DisplayName("new entity initializes version to zero with attribute_not_exists condition")
		void save_newEntity_initializesVersionToZeroWithNotExistsCondition() {
			// Arrange
			VersionedEntity entity = new VersionedEntity(ENTITY_ID, ENTITY_DATA);

			when(mockClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

			// Act
			template.save(entity);

			// Assert
			ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
			verify(mockClient).putItem(captor.capture());
			PutItemRequest request = captor.getValue();

			assertAll(() -> assertEquals(0L, entity.getVersion()),
					() -> assertTrue(request.item().containsKey(VERSION_ATTRIBUTE)),
					() -> assertEquals("0", request.item().get(VERSION_ATTRIBUTE).n()),
					() -> assertTrue(request.conditionExpression().contains("attribute_not_exists(#__version)")),
					() -> assertEquals(VERSION_ATTRIBUTE, request.expressionAttributeNames().get(VERSION_PLACEHOLDER)));
		}

		@Test
		@DisplayName("existing entity increments version and adds equality condition")
		void save_existingEntity_incrementsVersionWithEqualityCondition() {
			// Arrange
			long previousVersion = 5L;
			VersionedEntity entity = new VersionedEntity(ENTITY_ID, ENTITY_DATA);
			entity.setVersion(previousVersion);

			when(mockClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

			// Act
			template.save(entity);

			// Assert
			ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
			verify(mockClient).putItem(captor.capture());
			PutItemRequest request = captor.getValue();

			assertAll(() -> assertEquals(previousVersion + 1, entity.getVersion()),
					() -> assertTrue(request.item().containsKey(VERSION_ATTRIBUTE)),
					() -> assertEquals("6", request.item().get(VERSION_ATTRIBUTE).n()),
					() -> assertTrue(request.conditionExpression().contains("#__version = :__prevVersion")),
					() -> assertEquals(VERSION_ATTRIBUTE, request.expressionAttributeNames().get(VERSION_PLACEHOLDER)),
					() -> assertTrue(request.expressionAttributeValues().containsKey(PREV_VERSION_PLACEHOLDER)),
					() -> assertEquals("5", request.expressionAttributeValues().get(PREV_VERSION_PLACEHOLDER).n()));
		}

		@Test
		@DisplayName("stale version throws OptimisticLockingFailureException")
		void save_staleVersion_throwsOptimisticLockingFailure() {
			// Arrange
			long staleVersion = 5L;
			VersionedEntity entity = new VersionedEntity(ENTITY_ID, ENTITY_DATA);
			entity.setVersion(staleVersion);

			when(mockClient.putItem(any(PutItemRequest.class)))
					.thenThrow(ConditionalCheckFailedException.builder().message("Conditional check failed").build());

			// Act & Assert
			OptimisticLockingFailureException ex = assertThrows(OptimisticLockingFailureException.class,
					() -> template.save(entity));

			assertAll(() -> assertTrue(ex.getMessage().contains("Version mismatch")),
					() -> assertTrue(ex.getMessage().contains("expected version: 5")));
		}

		@Test
		@DisplayName("user condition is combined with version condition using AND")
		void save_withUserCondition_combinesBothConditions() {
			// Arrange
			VersionedEntity entity = new VersionedEntity(ENTITY_ID, ENTITY_DATA);

			Map<String, String> userNames = new HashMap<>();
			userNames.put("#data", "data");
			Map<String, Object> userValues = new HashMap<>();
			userValues.put(":val", "someValue");

			var conditionRequest = DynamoDbConditionRequest.Builder.request().withConditionExpression("#data = :val")
					.withExpressionAttributeNames(userNames).withExpressionAttributeValues(userValues).build();

			when(mockClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

			// Act
			template.save(entity, conditionRequest);

			// Assert
			ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
			verify(mockClient).putItem(captor.capture());
			PutItemRequest request = captor.getValue();

			assertAll(() -> assertTrue(request.conditionExpression().contains("#data = :val")),
					() -> assertTrue(request.conditionExpression().contains("AND")),
					() -> assertTrue(request.conditionExpression().contains("attribute_not_exists(#__version)")),
					() -> assertEquals("data", request.expressionAttributeNames().get("#data")),
					() -> assertEquals(VERSION_ATTRIBUTE, request.expressionAttributeNames().get(VERSION_PLACEHOLDER)),
					() -> assertTrue(request.expressionAttributeValues().containsKey(":val")));
		}
	}

	@Nested
	@DisplayName("update — versioned entity")
	class UpdateVersioned {

		@Test
		@DisplayName("increments version and adds equality condition on previous version")
		void update_existingEntity_incrementsVersionWithEqualityCondition() {
			// Arrange
			long previousVersion = 3L;
			VersionedEntity entity = new VersionedEntity(ENTITY_ID, ENTITY_DATA);
			entity.setVersion(previousVersion);

			Map<String, AttributeValue> returnedAttributes = new HashMap<>();
			returnedAttributes.put("id", AttributeValue.builder().s(ENTITY_ID).build());
			returnedAttributes.put("data", AttributeValue.builder().s(ENTITY_DATA).build());
			returnedAttributes.put(VERSION_ATTRIBUTE, AttributeValue.builder().n("4").build());

			when(mockClient.updateItem(any(UpdateItemRequest.class)))
					.thenReturn(UpdateItemResponse.builder().attributes(returnedAttributes).build());

			// Act
			template.update(entity);

			// Assert
			ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
			verify(mockClient).updateItem(captor.capture());
			UpdateItemRequest request = captor.getValue();

			assertAll(() -> assertEquals(previousVersion + 1, entity.getVersion()),
					() -> assertTrue(request.conditionExpression().contains("#__version = :__prevVersion")),
					() -> assertEquals(VERSION_ATTRIBUTE, request.expressionAttributeNames().get(VERSION_PLACEHOLDER)),
					() -> assertTrue(request.expressionAttributeValues().containsKey(PREV_VERSION_PLACEHOLDER)),
					() -> assertEquals("3", request.expressionAttributeValues().get(PREV_VERSION_PLACEHOLDER).n()));
		}

		@Test
		@DisplayName("stale version throws OptimisticLockingFailureException")
		void update_staleVersion_throwsOptimisticLockingFailure() {
			// Arrange
			long staleVersion = 3L;
			VersionedEntity entity = new VersionedEntity(ENTITY_ID, ENTITY_DATA);
			entity.setVersion(staleVersion);

			when(mockClient.updateItem(any(UpdateItemRequest.class)))
					.thenThrow(ConditionalCheckFailedException.builder().message("Conditional check failed").build());

			// Act & Assert
			OptimisticLockingFailureException ex = assertThrows(OptimisticLockingFailureException.class,
					() -> template.update(entity));

			assertAll(() -> assertTrue(ex.getMessage().contains("Version mismatch")),
					() -> assertTrue(ex.getMessage().contains("expected version: 3")));
		}
	}

	@Nested
	@DisplayName("non-versioned entity — no version conditions applied")
	class NonVersioned {

		@Test
		@DisplayName("save does not include version attribute or condition expression")
		void save_nonVersionedEntity_noVersionCondition() {
			// Arrange
			NonVersionedEntity entity = new NonVersionedEntity(ENTITY_ID, ENTITY_DATA);

			when(mockClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

			// Act
			template.save(entity);

			// Assert
			ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
			verify(mockClient).putItem(captor.capture());
			PutItemRequest request = captor.getValue();

			assertAll(() -> assertFalse(request.item().containsKey(VERSION_ATTRIBUTE)),
					() -> assertTrue(request.conditionExpression() == null || request.conditionExpression().isEmpty()));
		}

		@Test
		@DisplayName("update does not include version condition expression")
		void update_nonVersionedEntity_noVersionCondition() {
			// Arrange
			NonVersionedEntity entity = new NonVersionedEntity(ENTITY_ID, ENTITY_DATA);

			Map<String, AttributeValue> returnedAttributes = new HashMap<>();
			returnedAttributes.put("id", AttributeValue.builder().s(ENTITY_ID).build());
			returnedAttributes.put("data", AttributeValue.builder().s(ENTITY_DATA).build());

			when(mockClient.updateItem(any(UpdateItemRequest.class)))
					.thenReturn(UpdateItemResponse.builder().attributes(returnedAttributes).build());

			// Act
			template.update(entity);

			// Assert
			ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
			verify(mockClient).updateItem(captor.capture());
			UpdateItemRequest request = captor.getValue();

			assertTrue(request.conditionExpression() == null || request.conditionExpression().isEmpty());
		}
	}
}
