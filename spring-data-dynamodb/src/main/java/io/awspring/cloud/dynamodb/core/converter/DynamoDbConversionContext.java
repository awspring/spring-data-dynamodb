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
package io.awspring.cloud.dynamodb.core.converter;

import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentProperty;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.util.Assert;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbConversionContext implements ValueConversionContext<DynamoDbPersistentProperty> {

	private final DynamoDbPersistentProperty property;

	public DynamoDbConversionContext(DynamoDbPersistentProperty property) {
		Assert.notNull(property, "DynamoDbPersistentProperty must not be null");
		this.property = property;
	}

	@Override
	public DynamoDbPersistentProperty getProperty() {
		return this.property;
	}
}
