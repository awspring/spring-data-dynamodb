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

import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentProperty;
import java.util.Locale;
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.convert.ValueConversionContext;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Normalizes email values before writing them to DynamoDB. */
public class NormalizedEmailConverter
		implements PropertyValueConverter<String, AttributeValue, ValueConversionContext<DynamoDbPersistentProperty>> {

	@Override
	public String read(AttributeValue value, ValueConversionContext<DynamoDbPersistentProperty> context) {
		return value.s();
	}

	@Override
	public AttributeValue write(String value, ValueConversionContext<DynamoDbPersistentProperty> context) {
		return AttributeValue.builder().s(value.trim().toLowerCase(Locale.ROOT)).build();
	}
}
