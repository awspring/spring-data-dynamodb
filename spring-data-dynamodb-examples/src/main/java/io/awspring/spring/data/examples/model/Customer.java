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
import org.springframework.data.convert.ValueConverter;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
@Table(tableName = "Commerce")
public class Customer {

	@PartitionKey
	private String pk;

	@SortKey
	private String sk;

	private String customerId;

	private String username;

	@ValueConverter(NormalizedEmailConverter.class)
	private String email;

	@InnerClass(serializeAsNestedMap = true)
	private Address address;

	@Column("gsi1pk")
	private String gsi1pk;

	@Column("gsi1sk")
	private String gsi1sk;

	public Customer() {
	}

	public static Customer of(String id, String name, String email, Address address) {
		Customer customer = new Customer();
		customer.pk = "CUSTOMER#" + id;
		customer.sk = "#PROFILE";
		customer.customerId = id;
		customer.username = name;
		customer.email = email;
		customer.address = address;
		customer.gsi1pk = "EMAIL#" + email;
		customer.gsi1sk = "#PROFILE";
		return customer;
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

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Address getAddress() {
		return address;
	}

	public void setAddress(Address address) {
		this.address = address;
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
}
