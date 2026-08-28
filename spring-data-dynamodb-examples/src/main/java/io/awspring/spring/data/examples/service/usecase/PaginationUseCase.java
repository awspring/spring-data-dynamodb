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
package io.awspring.spring.data.examples.service.usecase;

import io.awspring.spring.data.dynamodb.core.DynamoDbTemplate;
import io.awspring.spring.data.dynamodb.core.EntityQueryResult;
import io.awspring.spring.data.dynamodb.request.DynamoDbPageRequest;
import io.awspring.spring.data.dynamodb.request.DynamoDbQueryRequest;
import io.awspring.spring.data.examples.model.OrderItem;
import io.awspring.spring.data.examples.repository.OrderItemRepository;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Component;

/** Demonstrates opaque LastEvaluatedKey pagination through Window and template APIs. */
@Component
@Order(80)
public class PaginationUseCase implements ExampleUseCase {

	private final OrderItemRepository items;
	private final DynamoDbTemplate template;

	public PaginationUseCase(OrderItemRepository items, DynamoDbTemplate template) {
		this.items = items;
		this.template = template;
	}

	@Override
	public String title() {
		return "Window keyset scrolling and DynamoDbPageRequest";
	}

	@Override
	public void run() {
		Window<OrderItem> window = items.findWindowByPkAndOrderId(ExampleData.CUSTOMER_PK, ExampleData.ORDER_ID,
				ScrollPosition.keyset(), Limit.of(2));
		DynamoDbQueryRequest request = DynamoDbQueryRequest.Builder.request()
				.withKeyConditionExpression("#pk = :pk AND begins_with(#sk, :prefix)")
				.withExpressionAttributeNames(Map.of("#pk", "pk", "#sk", "sk")).withExpressionAttributeValues(
						Map.of(":pk", ExampleData.CUSTOMER_PK, ":prefix", ExampleData.ITEM_PREFIX))
				.build();
		EntityQueryResult<List<OrderItem>> page = template.query(OrderItem.class, request, DynamoDbPageRequest.of(2));
		System.out.println("Window size=" + window.size() + ", hasNext=" + window.hasNext() + ", template page="
				+ page.getEntity().size() + ", cursor=" + (page.getLastEvaluatedKey() != null));
	}
}
