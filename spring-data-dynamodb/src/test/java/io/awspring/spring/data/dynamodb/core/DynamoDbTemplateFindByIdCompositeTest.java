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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.Column;
import io.awspring.spring.data.dynamodb.core.mapping.Derived;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.request.DynamoDbUpdateExpressionRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

@DisplayName("DynamoDbTemplate -- composite and template-owned keys")
class DynamoDbTemplateFindByIdCompositeTest {

	@Table(tableName = "Commerce")
	static class Order {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		@Column("gsi1pk")
		String gsi1pk;
		@Column("gsi1sk")
		String gsi1sk;
		@Column("gsi2pk")
		String gsi2pk;
		@Column("gsi2sk")
		String gsi2sk;

		public Order() {
		}
	}

	@Table(tableName = "Commerce")
	@SortKeyTemplate("ORDER#{orderId}#ITEM#{productId}")
	static class TemplatedItem {
		@PartitionKey
		String pk;
		String orderId;
		@Derived
		String productId;
		String name;

		public TemplatedItem() {
		}
	}

	private DynamoDbClient client;
	private DynamoDbTemplate template;

	@BeforeEach
	void setUp() {
		client = mock(DynamoDbClient.class);
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Order.class);
		mappingContext.getRequiredPersistentEntity(TemplatedItem.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		template = new DynamoDbTemplate(client, converter);
	}

	@Test
	@DisplayName("findById(pk, sk) binds both keys on an entity that also declares GSI @Column attributes")
	void findByIdBindsBothKeysDespiteGsiColumns() {
		when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());

		template.findById("CUSTOMER#12345", "ORDER#1321", Order.class);

		ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
		verify(client).getItem(captor.capture());
		Map<String, AttributeValue> key = captor.getValue().key();

		assertEquals(AttributeValue.builder().s("CUSTOMER#12345").build(), key.get("pk"));
		assertEquals(AttributeValue.builder().s("ORDER#1321").build(), key.get("sk"));
	}

	@Test
	@DisplayName("findById(pk, sk) binds the composed value to the @SortKeyTemplate column when no @SortKey exists")
	void findByIdBindsTemplateSortKeyColumn() {
		when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());

		template.findById("CUSTOMER#12345", "ORDER#1321#ITEM#WIDGET-1", TemplatedItem.class);

		ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
		verify(client).getItem(captor.capture());
		Map<String, AttributeValue> key = captor.getValue().key();

		assertEquals(AttributeValue.builder().s("CUSTOMER#12345").build(), key.get("pk"));
		assertEquals(AttributeValue.builder().s("ORDER#1321#ITEM#WIDGET-1").build(), key.get("sk"));
	}

	@Test
	@DisplayName("existsById(pk, sk) binds a template-owned sort key")
	void existsByIdBindsTemplateSortKeyColumn() {
		when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
				.item(Map.of("pk", AttributeValue.builder().s("CUSTOMER#12345").build())).build());

		template.existsById("CUSTOMER#12345", "ORDER#1321#ITEM#WIDGET-1", TemplatedItem.class);

		ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
		verify(client).getItem(captor.capture());
		assertTemplateKey(captor.getValue().key());
	}

	@Test
	@DisplayName("delete(type, pk, sk) binds a template-owned sort key")
	void deleteByKeyBindsTemplateSortKeyColumn() {
		when(client.deleteItem(any(DeleteItemRequest.class))).thenReturn(DeleteItemResponse.builder().build());

		template.delete(TemplatedItem.class, "CUSTOMER#12345", "ORDER#1321#ITEM#WIDGET-1");

		ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
		verify(client).deleteItem(captor.capture());
		assertTemplateKey(captor.getValue().key());
	}

	@Test
	@DisplayName("delete(entity) composes the template-owned sort key")
	void deleteEntityComposesTemplateSortKeyColumn() {
		when(client.deleteItem(any(DeleteItemRequest.class))).thenReturn(DeleteItemResponse.builder().build());

		template.delete(templatedItem());

		ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
		verify(client).deleteItem(captor.capture());
		assertTemplateKey(captor.getValue().key());
	}

	@Test
	@DisplayName("update(entity) composes the key and does not update the template-owned primary key")
	void updateEntityComposesTemplateKeyWithoutUpdatingIt() {
		when(client.updateItem(any(UpdateItemRequest.class))).thenReturn(UpdateItemResponse.builder().build());

		template.update(templatedItem());

		ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
		verify(client).updateItem(captor.capture());
		UpdateItemRequest request = captor.getValue();
		assertTemplateKey(request.key());
		assertFalse(request.expressionAttributeNames().containsValue("sk"));
		assertTrue(request.expressionAttributeNames().containsValue("name"));
	}

	@Test
	@DisplayName("update(pk, sk) binds a template-owned sort key")
	void updateByKeyBindsTemplateSortKeyColumn() {
		Map<String, AttributeValue> attributes = Map.of("pk", AttributeValue.builder().s("CUSTOMER#12345").build(),
				"sk", AttributeValue.builder().s("ORDER#1321#ITEM#WIDGET-1").build());
		when(client.updateItem(any(UpdateItemRequest.class)))
				.thenReturn(UpdateItemResponse.builder().attributes(attributes).build());
		DynamoDbUpdateExpressionRequest request = DynamoDbUpdateExpressionRequest.Builder.builder()
				.withUpdateExpression("SET #name = :name").withExpressionAttributeNames(Map.of("#name", "name"))
				.withExpressionAttributeValues(Map.of(":name", "Widget")).build();

		template.update("CUSTOMER#12345", "ORDER#1321#ITEM#WIDGET-1", request, TemplatedItem.class);

		ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
		verify(client).updateItem(captor.capture());
		assertTemplateKey(captor.getValue().key());
	}

	private static TemplatedItem templatedItem() {
		TemplatedItem item = new TemplatedItem();
		item.pk = "CUSTOMER#12345";
		item.orderId = "1321";
		item.productId = "WIDGET-1";
		item.name = "Widget";
		return item;
	}

	private static void assertTemplateKey(Map<String, AttributeValue> key) {
		assertEquals(AttributeValue.builder().s("CUSTOMER#12345").build(), key.get("pk"));
		assertEquals(AttributeValue.builder().s("ORDER#1321#ITEM#WIDGET-1").build(), key.get("sk"));
	}
}
