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

import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SecondaryIndex;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import java.math.BigDecimal;

/** Read-only projection of orders through the status index. */
@SecondaryIndex(name = "GSI1", tableName = "Commerce")
public class OrderStatusIndex {

	@PartitionKey("gsi1pk")
	private String statusKey;

	@SortKey("gsi1sk")
	private String createdAtKey;

	private String pk;
	private String sk;
	private String orderId;
	private OrderStatus status;
	private BigDecimal total;

	public OrderStatusIndex() {
	}

	public String getStatusKey() {
		return statusKey;
	}

	public void setStatusKey(String statusKey) {
		this.statusKey = statusKey;
	}

	public String getCreatedAtKey() {
		return createdAtKey;
	}

	public void setCreatedAtKey(String createdAtKey) {
		this.createdAtKey = createdAtKey;
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
}
