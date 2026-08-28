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

import io.awspring.spring.data.examples.model.OrderItemCollection;
import io.awspring.spring.data.examples.model.OrderItemCollectionByIndex;
import io.awspring.spring.data.examples.repository.OrderItemCollectionByIndexRepository;
import io.awspring.spring.data.examples.repository.OrderItemCollectionRepository;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Demonstrates item-collection folding on a table and secondary index. */
@Component
@Order(70)
public class ItemCollectionUseCase implements ExampleUseCase {

	private final OrderItemCollectionRepository tableViews;
	private final OrderItemCollectionByIndexRepository indexViews;

	public ItemCollectionUseCase(OrderItemCollectionRepository tableViews,
			OrderItemCollectionByIndexRepository indexViews) {
		this.tableViews = tableViews;
		this.indexViews = indexViews;
	}

	@Override
	public String title() {
		return "Item collections: fixed, explicit, named, and index-backed";
	}

	@Override
	public void run() {
		Optional<OrderItemCollection> fixed = tableViews.findByPartitionKey(ExampleData.CUSTOMER_PK);
		Optional<OrderItemCollection> explicit = tableViews.findExplicit(ExampleData.CUSTOMER_PK,
				ExampleData.ITEM_PREFIX);
		Optional<OrderItemCollection> named = tableViews.findNamed(ExampleData.CUSTOMER_PK, ExampleData.ITEM_PREFIX);
		Optional<OrderItemCollectionByIndex> indexed = indexViews.findByPartitionKey("ORDER#" + ExampleData.ORDER_ID);
		System.out.println("fixed=" + fixed.isPresent() + ", explicit=" + explicit.isPresent() + ", named="
				+ named.isPresent() + ", index=" + indexed.isPresent());
	}
}
