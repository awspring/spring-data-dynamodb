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
package io.awspring.spring.data.examples.model;

import io.awspring.spring.data.dynamodb.core.mapping.AggregateItem;
import io.awspring.spring.data.dynamodb.core.mapping.AggregateTable;
import java.util.List;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
@AggregateTable(tableName = "Commerce", indexName = "GSI2", partitionKey = "gsi2pk", sortKey = "gsi2sk")
public class OrderByIndex {

	@AggregateItem(startsWith = "ORDER#")
	private Order order;

	@AggregateItem(startsWith = "ITEM#")
	private List<OrderItem> items;

	public Order getOrder() {
		return order;
	}

	public void setOrder(Order order) {
		this.order = order;
	}

	public List<OrderItem> getItems() {
		return items;
	}

	public void setItems(List<OrderItem> items) {
		this.items = items;
	}
}
