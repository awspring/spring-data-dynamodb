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
import io.awspring.spring.data.examples.model.Order;
import io.awspring.spring.data.examples.model.OrderItem;
import io.awspring.spring.data.examples.model.OrderStatus;
import io.awspring.spring.data.examples.repository.OrderItemRepository;
import io.awspring.spring.data.examples.repository.OrderRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Demonstrates @Update, state-based update, and delete operations. */
@Component
@org.springframework.core.annotation.Order(120)
public class UpdateDeleteUseCase implements ExampleUseCase {

	private final OrderRepository orders;
	private final OrderItemRepository items;
	private final DynamoDbTemplate template;

	public UpdateDeleteUseCase(OrderRepository orders, OrderItemRepository items, DynamoDbTemplate template) {
		this.orders = orders;
		this.items = items;
		this.template = template;
	}

	@Override
	public String title() {
		return "@Update, template update, and delete";
	}

	@Override
	public void run() {
		Order modified = orders.changeStatus(ExampleData.CUSTOMER_PK, ExampleData.ORDER_SK, OrderStatus.DELIVERED,
				"STATUS#" + OrderStatus.DELIVERED);
		Order current = template.findById(ExampleData.CUSTOMER_PK, ExampleData.ORDER_SK, Order.class);
		if (current != null) {
			current.setTotal(new BigDecimal("134.99"));
			template.update(current);
		}
		items.delete(OrderItem.of(ExampleData.CUSTOMER_ID, ExampleData.ORDER_ID, "CABLE-3", "Cable", 3,
				new BigDecimal("4.99")));
		System.out.println("updated status=" + modified.getStatus() + ", updated total and deleted CABLE-3");
	}
}
