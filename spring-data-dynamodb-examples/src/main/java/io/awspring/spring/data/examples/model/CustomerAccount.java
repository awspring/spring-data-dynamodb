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

import io.awspring.spring.data.dynamodb.core.mapping.InnerClass;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
@Table(tableName = "Commerce")
public class CustomerAccount {

	@PartitionKey
	private String pk;

	@SortKey
	private String sk;

	@InnerClass(startsWith = "PROFILE#")
	private AccountProfile profile;

	@InnerClass(startsWith = "PAYMENT#")
	private PaymentMethod payment;

	@InnerClass(startsWith = "NOTE#")
	private AccountNote note;

	public CustomerAccount() {
	}

	public static CustomerAccount profile(String accountId, AccountProfile profile) {
		CustomerAccount account = new CustomerAccount();
		account.pk = "ACCOUNT#" + accountId;
		account.sk = "PROFILE#" + accountId;
		account.profile = profile;
		return account;
	}

	public static CustomerAccount payment(String accountId, String methodId, PaymentMethod payment) {
		CustomerAccount account = new CustomerAccount();
		account.pk = "ACCOUNT#" + accountId;
		account.sk = "PAYMENT#" + methodId;
		account.payment = payment;
		return account;
	}

	public static CustomerAccount note(String accountId, String noteId, AccountNote note) {
		CustomerAccount account = new CustomerAccount();
		account.pk = "ACCOUNT#" + accountId;
		account.sk = "NOTE#" + noteId;
		account.note = note;
		return account;
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

	public AccountProfile getProfile() {
		return profile;
	}

	public void setProfile(AccountProfile profile) {
		this.profile = profile;
	}

	public PaymentMethod getPayment() {
		return payment;
	}

	public void setPayment(PaymentMethod payment) {
		this.payment = payment;
	}

	public AccountNote getNote() {
		return note;
	}

	public void setNote(AccountNote note) {
		this.note = note;
	}
}
