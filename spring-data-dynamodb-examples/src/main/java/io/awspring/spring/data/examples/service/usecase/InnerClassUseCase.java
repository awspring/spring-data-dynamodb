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

import io.awspring.spring.data.dynamodb.repository.DynamoDbCompositeId;
import io.awspring.spring.data.examples.model.AccountNote;
import io.awspring.spring.data.examples.model.AccountProfile;
import io.awspring.spring.data.examples.model.Customer;
import io.awspring.spring.data.examples.model.CustomerAccount;
import io.awspring.spring.data.examples.model.PaymentMethod;
import io.awspring.spring.data.examples.repository.CustomerAccountRepository;
import io.awspring.spring.data.examples.repository.CustomerRepository;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Demonstrates nested-map and routed flattened @InnerClass mappings. */
@Component
@Order(90)
public class InnerClassUseCase implements ExampleUseCase {

	private final CustomerRepository customers;
	private final CustomerAccountRepository accounts;

	public InnerClassUseCase(CustomerRepository customers, CustomerAccountRepository accounts) {
		this.customers = customers;
		this.accounts = accounts;
	}

	@Override
	public String title() {
		return "@InnerClass nested values and routed row containers";
	}

	@Override
	public void run() {
		customers.findById(DynamoDbCompositeId.of(ExampleData.CUSTOMER_PK, ExampleData.PROFILE_SK))
				.map(Customer::getAddress)
				.ifPresent(address -> System.out.println("nested address -> " + address.getCity()));
		accounts.save(CustomerAccount.profile(ExampleData.CUSTOMER_ID, AccountProfile.of("Matej", "GOLD")));
		accounts.save(CustomerAccount.payment(ExampleData.CUSTOMER_ID, "pm-1", PaymentMethod.of("VISA", "4242")));
		accounts.save(CustomerAccount.note(ExampleData.CUSTOMER_ID, "n-1", AccountNote.of("VIP customer")));
		List<CustomerAccount> rows = accounts.findByPk(ExampleData.ACCOUNT_PK);
		System.out.println("routed account rows -> " + rows.size());
	}
}
