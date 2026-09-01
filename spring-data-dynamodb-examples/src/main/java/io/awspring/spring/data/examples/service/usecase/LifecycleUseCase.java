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
import io.awspring.spring.data.examples.repository.CustomerRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Demonstrates entity callbacks and mapping application events. */
@Component
@Order(110)
public class LifecycleUseCase implements ExampleUseCase {

	private final CustomerRepository customers;
	private final CustomerLifecycleCallbacks lifecycle;

	public LifecycleUseCase(CustomerRepository customers, CustomerLifecycleCallbacks lifecycle) {
		this.customers = customers;
		this.lifecycle = lifecycle;
	}

	@Override
	public String title() {
		return "Lifecycle callbacks and mapping events";
	}

	@Override
	public void run() {
		customers.findById(DynamoDbCompositeId.of(ExampleData.CUSTOMER_PK, ExampleData.PROFILE_SK))
				.ifPresent(customers::save);
		System.out.println("customer callback invocations=" + lifecycle.callbackInvocations() + ", after-save events="
				+ lifecycle.afterSaveEvents());
	}
}
