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
import io.awspring.spring.data.dynamodb.core.EntityWriteResult;
import io.awspring.spring.data.examples.model.Order;
import io.awspring.spring.data.examples.model.OrderItem;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** Demonstrates generic template results, conditional insert, and BatchWriteItem. */
@Component
@org.springframework.core.annotation.Order(20)
public class TemplateOperationsUseCase implements ExampleUseCase {

	private final DynamoDbTemplate template;

	public TemplateOperationsUseCase(DynamoDbTemplate template) {
		this.template = template;
	}

	@Override
	public String title() {
		return "DynamoDbTemplate reads, writes, insert conditions, and batches";
	}

	@Override
	public void run() {
		OrderItem cable = OrderItem.of(ExampleData.CUSTOMER_ID, ExampleData.ORDER_ID, "CABLE-3", "Cable", 3,
				new BigDecimal("4.99"));
		EntityWriteResult<OrderItem> inserted = template.insert(cable);
		template.saveAll(List.of(
				OrderItem.of(ExampleData.CUSTOMER_ID, ExampleData.ORDER_ID, "CASE-2", "Case", 1,
						new BigDecimal("8.99")),
				OrderItem.of(ExampleData.CUSTOMER_ID, ExampleData.ORDER_ID, "ADAPTER-4", "Adapter", 1,
						new BigDecimal("12.99"))));
		Order order = template.findById(ExampleData.CUSTOMER_PK, ExampleData.ORDER_SK, Order.class);
		System.out.println("inserted " + ExampleData.ITEM_PREFIX + inserted.getEntity().getProductId()
				+ "; template read status=" + (order != null ? order.getStatus() : null));
	}
}
