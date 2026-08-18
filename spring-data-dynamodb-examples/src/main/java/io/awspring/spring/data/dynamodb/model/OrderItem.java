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
package io.awspring.spring.data.dynamodb.model;

import io.awspring.cloud.dynamodb.core.mapping.Column;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.math.BigDecimal;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
@Table(tableName = "Commerce")
public class OrderItem {

	@PartitionKey
	private String pk;

	@SortKey
	private String sk;

	private String customerId;

	private String orderId;

	private String productId;

	private String productName;

	private int quantity;

	private BigDecimal price;

	@Column("gsi1pk")
	private String gsi1pk;

	@Column("gsi1sk")
	private String gsi1sk;

	@Column("gsi2pk")
	private String gsi2pk;

	@Column("gsi2sk")
	private String gsi2sk;

	public OrderItem() {
	}

	public static OrderItem of(String customerId, String orderId, String productId, String productName, int quantity,
			BigDecimal price) {
		OrderItem item = new OrderItem();
		item.pk = "CUSTOMER#" + customerId;
		item.sk = "ORDER#" + orderId + "#ITEM#" + productId;
		item.customerId = customerId;
		item.orderId = orderId;
		item.productId = productId;
		item.productName = productName;
		item.quantity = quantity;
		item.price = price;
		item.gsi1pk = "PRODUCT#" + productId;
		item.gsi1sk = "ORDER#" + orderId;
		item.gsi2pk = "ORDER#" + orderId;
		item.gsi2sk = "ITEM#" + productId;
		return item;
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

	public String getProductId() {
		return productId;
	}

	public void setProductId(String productId) {
		this.productId = productId;
	}

	public String getProductName() {
		return productName;
	}

	public void setProductName(String productName) {
		this.productName = productName;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
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
}
