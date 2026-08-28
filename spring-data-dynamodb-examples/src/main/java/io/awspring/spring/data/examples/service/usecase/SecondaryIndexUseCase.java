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

import io.awspring.spring.data.examples.model.OrderStatus;
import io.awspring.spring.data.examples.model.OrderStatusIndex;
import io.awspring.spring.data.examples.repository.OrderStatusIndexRepository;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Demonstrates a typed, read-only SecondaryIndexRepository. */
@Component
@Order(60)
public class SecondaryIndexUseCase implements ExampleUseCase {

	private final OrderStatusIndexRepository ordersByStatus;

	public SecondaryIndexUseCase(OrderStatusIndexRepository ordersByStatus) {
		this.ordersByStatus = ordersByStatus;
	}

	@Override
	public String title() {
		return "Typed @SecondaryIndex repository";
	}

	@Override
	public void run() {
		List<OrderStatusIndex> shipped = ordersByStatus
				.findTop2ByStatusKeyOrderByCreatedAtKeyDesc("STATUS#" + OrderStatus.SHIPPED);
		System.out.println("latest SHIPPED orders -> " + shipped.size());
	}
}
