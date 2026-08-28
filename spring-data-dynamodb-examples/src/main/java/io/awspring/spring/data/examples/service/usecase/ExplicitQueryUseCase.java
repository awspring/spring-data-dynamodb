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

import io.awspring.spring.data.examples.repository.CustomerRepository;
import io.awspring.spring.data.examples.repository.OrderItemRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Demonstrates explicit filter expressions, scan opt-in, and PartiQL. */
@Component
@Order(40)
public class ExplicitQueryUseCase implements ExampleUseCase {

	private final CustomerRepository customers;
	private final OrderItemRepository items;

	public ExplicitQueryUseCase(CustomerRepository customers, OrderItemRepository items) {
		this.customers = customers;
		this.items = items;
	}

	@Override
	public String title() {
		return "Explicit @Query, @AllowScan, and PartiQL";
	}

	@Override
	public void run() {
		int explicit = customers.findExplicitByEmail(ExampleData.EMAIL).size();
		int derivedScan = customers.findByEmail(ExampleData.EMAIL).size();
		int productScan = items.scanByProductName("Widget").size();
		int partiQl = items.findWithPartiQl(ExampleData.CUSTOMER_PK, ExampleData.ITEM_PREFIX).size();
		System.out.println("explicit=" + explicit + ", derived scan=" + derivedScan + ", product scan=" + productScan
				+ ", PartiQL=" + partiQl);
	}
}
