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

import io.awspring.spring.data.dynamodb.core.mapping.Column;
import io.awspring.spring.data.dynamodb.core.mapping.InnerClass;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
@Table(tableName = "Commerce")
public class Order {

	@PartitionKey
	private String pk;

	@SortKey
	private String sk;

	private String customerId;

	private String orderId;

	private OrderStatus status;

	private BigDecimal total;

	private Instant createdAt;

	@InnerClass(serializeAsNestedMap = true)
	private Address shippingAddress;

	@Column("gsi1pk")
	private String gsi1pk;

	@Column("gsi1sk")
	private String gsi1sk;

	@Column("gsi2pk")
	private String gsi2pk;

	@Column("gsi2sk")
	private String gsi2sk;

	public Order() {
	}

	public static Order of(String customerId, String orderId, Instant createdAt, OrderStatus status, BigDecimal total,
			Address shippingAddress) {
		Order order = new Order();
		order.pk = "CUSTOMER#" + customerId;
		order.sk = "ORDER#" + orderId;
		order.customerId = customerId;
		order.orderId = orderId;
		order.status = status;
		order.total = total;
		order.createdAt = createdAt;
		order.shippingAddress = shippingAddress;
		order.gsi1pk = "STATUS#" + status;
		order.gsi1sk = createdAt.toString();
		order.gsi2pk = "ORDER#" + orderId;
		order.gsi2sk = "ORDER#" + orderId;
		return order;
	}

	public String getPk() {
		return pk;
	}

	public void setPk(String pk) {
		this.pk = pk;
	}

	public String getSk() {
		return sk;
	}

	public void setSk(String sk) {
		this.sk = sk;
	}

	public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public void setStatus(OrderStatus status) {
		this.status = status;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public void setTotal(BigDecimal total) {
		this.total = total;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public String getGsi1pk() {
		return gsi1pk;
	}

	public void setGsi1pk(String gsi1pk) {
		this.gsi1pk = gsi1pk;
	}

	public String getGsi1sk() {
		return gsi1sk;
	}

	public void setGsi1sk(String gsi1sk) {
		this.gsi1sk = gsi1sk;
	}

	public String getGsi2pk() {
		return gsi2pk;
	}

	public void setGsi2pk(String gsi2pk) {
		this.gsi2pk = gsi2pk;
	}

	public String getGsi2sk() {
		return gsi2sk;
	}

	public void setGsi2sk(String gsi2sk) {
		this.gsi2sk = gsi2sk;
	}

	public Address getShippingAddress() {
		return shippingAddress;
	}

	public void setShippingAddress(Address shippingAddress) {
		this.shippingAddress = shippingAddress;
	}
}
