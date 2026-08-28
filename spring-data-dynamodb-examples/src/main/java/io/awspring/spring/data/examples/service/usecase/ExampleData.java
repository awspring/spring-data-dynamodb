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

final class ExampleData {

	static final String CUSTOMER_ID = "12345";
	static final String ORDER_ID = "1321";
	static final String CUSTOMER_PK = "CUSTOMER#" + CUSTOMER_ID;
	static final String ACCOUNT_PK = "ACCOUNT#" + CUSTOMER_ID;
	static final String PROFILE_SK = "#PROFILE";
	static final String ORDER_SK = "ORDER#" + ORDER_ID;
	static final String ITEM_PREFIX = ORDER_SK + "#ITEM#";
	static final String EMAIL = "matej@example.com";

	private ExampleData() {
	}
}
