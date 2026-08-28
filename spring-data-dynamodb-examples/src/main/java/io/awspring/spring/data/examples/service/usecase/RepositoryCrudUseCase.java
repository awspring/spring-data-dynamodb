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

import io.awspring.spring.data.examples.model.Address;
import io.awspring.spring.data.examples.model.Customer;
import io.awspring.spring.data.examples.model.Order;
import io.awspring.spring.data.examples.model.OrderItem;
import io.awspring.spring.data.examples.model.OrderStatus;
import io.awspring.spring.data.examples.repository.CustomerRepository;
import io.awspring.spring.data.examples.repository.OrderItemRepository;
import io.awspring.spring.data.examples.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Seeds the table and demonstrates repository save/saveAll operations. */
@Component
@org.springframework.core.annotation.Order(10)
public class RepositoryCrudUseCase implements ExampleUseCase {

	private final CustomerRepository customers;
	private final OrderRepository orders;
	private final OrderItemRepository items;

	public RepositoryCrudUseCase(CustomerRepository customers, OrderRepository orders, OrderItemRepository items) {
		this.customers = customers;
		this.orders = orders;
		this.items = items;
	}

	@Override
	public String title() {
		return "Repository CRUD and optimistic locking";
	}

	@Override
	public void run() {
		customers.save(Customer.of(ExampleData.CUSTOMER_ID, "Matej", ExampleData.EMAIL,
				Address.of("1 Ilica", "Zagreb", "HR")));
		Order saved = orders.save(Order.of(ExampleData.CUSTOMER_ID, ExampleData.ORDER_ID, Instant.now(),
				OrderStatus.SHIPPED, new BigDecimal("129.99"), Address.of("10 Warehouse Rd", "Split", "HR")));
		items.saveAll(List.of(
				OrderItem.of(ExampleData.CUSTOMER_ID, ExampleData.ORDER_ID, "WIDGET-1", "Widget", 2,
						new BigDecimal("19.99")),
				OrderItem.of(ExampleData.CUSTOMER_ID, ExampleData.ORDER_ID, "GADGET-9", "Gadget", 1,
						new BigDecimal("89.99"))));
		System.out.println("saved customer, versioned order " + saved.getVersion() + ", and two items");
	}
}
