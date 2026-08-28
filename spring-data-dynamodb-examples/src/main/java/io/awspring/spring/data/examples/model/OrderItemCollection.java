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

import io.awspring.spring.data.dynamodb.core.mapping.ItemCollectionMember;
import io.awspring.spring.data.dynamodb.core.mapping.ItemCollectionView;
import java.util.List;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
@ItemCollectionView(tableName = "Commerce", partitionKey = "pk", sortKey = "sk")
public class OrderItemCollection {

	@ItemCollectionMember(regex = "^#PROFILE$")
	private Customer customer;

	@ItemCollectionMember(regex = "^ORDER#[^#]+$")
	private Order order;

	@ItemCollectionMember(regex = "^ORDER#[^#]+#ITEM#.+$")
	private List<OrderItem> orderItemList;

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}

	public Order getOrder() {
		return order;
	}

	public void setOrder(Order order) {
		this.order = order;
	}

	public List<OrderItem> getOrderItemList() {
		return orderItemList;
	}

	public void setOrderItemList(List<OrderItem> orderItemList) {
		this.orderItemList = orderItemList;
	}
}
