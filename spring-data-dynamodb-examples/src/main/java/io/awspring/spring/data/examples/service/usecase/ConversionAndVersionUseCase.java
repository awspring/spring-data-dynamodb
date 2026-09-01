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
import io.awspring.spring.data.examples.model.Customer;
import io.awspring.spring.data.examples.model.Order;
import io.awspring.spring.data.examples.model.OrderItem;
import org.springframework.stereotype.Component;

/** Demonstrates @ValueConverter, @Version, @SortKeyTemplate, and @Derived. */
@Component
@org.springframework.core.annotation.Order(100)
public class ConversionAndVersionUseCase implements ExampleUseCase {

	private final DynamoDbTemplate template;

	public ConversionAndVersionUseCase(DynamoDbTemplate template) {
		this.template = template;
	}

	@Override
	public String title() {
		return "Property conversion, optimistic versioning, and derived sort keys";
	}

	@Override
	public void run() {
		Customer customer = template.findById(ExampleData.CUSTOMER_PK, ExampleData.PROFILE_SK, Customer.class);
		Order order = template.findById(ExampleData.CUSTOMER_PK, ExampleData.ORDER_SK, Order.class);
		OrderItem item = template.findById(ExampleData.CUSTOMER_PK, ExampleData.ITEM_PREFIX + "WIDGET-1",
				OrderItem.class);
		System.out.println("normalized email=" + (customer != null ? customer.getEmail() : null) + ", version="
				+ (order != null ? order.getVersion() : null) + ", derived product="
				+ (item != null ? item.getProductId() : null));
	}
}
