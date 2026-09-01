/*
 * Copyright 2013-2025 the original author or authors.
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
package io.awspring.spring.data.dynamodb.core;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbAccessor implements InitializingBean {

	private @Nullable DynamoDbClient client;

	private DynamoDbExceptionTranslator exceptionTranslator = new DefaultDynamoDbExceptionTranslator();

	public DynamoDbExceptionTranslator getExceptionTranslator() {
		return this.exceptionTranslator;
	}

	public void setExceptionTranslator(DynamoDbExceptionTranslator exceptionTranslator) {

		Assert.notNull(exceptionTranslator, "DynamoDbExceptionTranslator must not be null");

		this.exceptionTranslator = exceptionTranslator;
	}

	@Nullable
	public DynamoDbClient getDynamoDbClient() {
		return this.client;
	}

	DynamoDbClient getCurrentDynamoDbClient() {

		DynamoDbClient client = getDynamoDbClient();

		Assert.state(client != null, "DynamoDbClient is null");

		return client;
	}

	public void setDynamoDbClient(DynamoDbClient client) {

		Assert.notNull(client, "DynamoDbClient must not be null");

		this.client = client;
	}

	@Override
	public void afterPropertiesSet() {
		Assert.state(client != null, "DynamoDbClient must not be null");
	}

	protected DataAccessException translate(String task, @Nullable String statement, RuntimeException ex) {

		Assert.notNull(ex, "RuntimeException must not be null");

		return getExceptionTranslator().translate(task, statement, ex);
	}

}
