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

import io.awspring.spring.data.examples.model.Order;
import io.awspring.spring.data.examples.model.OrderItem;
import io.awspring.spring.data.examples.repository.OrderItemRepository;
import io.awspring.spring.data.examples.repository.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Demonstrates derived key queries, limits, ordering, count, and exists. */
@Component
@org.springframework.core.annotation.Order(30)
public class DerivedQueryUseCase implements ExampleUseCase {

	private final OrderRepository orders;
	private final OrderItemRepository items;

	public DerivedQueryUseCase(OrderRepository orders, OrderItemRepository items) {
		this.orders = orders;
		this.items = items;
	}

	@Override
	public String title() {
		return "Derived queries, Top, OrderBy, count, and exists";
	}

	@Override
	public void run() {
		List<Order> ordered = orders.findByPkOrderBySkDesc(ExampleData.CUSTOMER_PK);
		List<Order> top = orders.findTop1ByPkOrderBySkDesc(ExampleData.CUSTOMER_PK);
		List<OrderItem> firstItems = items.findTop2ByPkAndOrderId(ExampleData.CUSTOMER_PK, ExampleData.ORDER_ID);
		System.out.println("orders=" + ordered.size() + ", top=" + top.size() + ", first items=" + firstItems.size()
				+ ", count=" + orders.countByPk(ExampleData.CUSTOMER_PK) + ", exists="
				+ orders.existsByPk(ExampleData.CUSTOMER_PK));
	}
}
