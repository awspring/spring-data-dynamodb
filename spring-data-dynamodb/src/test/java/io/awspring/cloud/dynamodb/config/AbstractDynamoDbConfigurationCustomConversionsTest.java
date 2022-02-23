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
package io.awspring.cloud.dynamodb.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.awspring.cloud.dynamodb.core.converter.DynamoDbConversions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

class AbstractDynamoDbConfigurationCustomConversionsTest {

	@Test
	void defaultConfigurationRegistersNoCustomConverters() {

		TestConfiguration configuration = new TestConfiguration();

		assertThat(configuration.customConverters()).isEmpty();
		assertThat(configuration.customConversions()).isNotNull();
	}

	@Test
	void subclassCanContributeConvertersViaCustomConverters() {

		CustomConvertersConfiguration configuration = new CustomConvertersConfiguration();

		DynamoDbConversions conversions = configuration.customConversions();

		assertThat(configuration.customConverters()).hasSize(1);
		assertThat(conversions.hasCustomWriteTarget(StringBuilder.class, String.class)).isTrue();
	}

	static class TestConfiguration extends AbstractDynamoDbConfiguration {
	}

	static class CustomConvertersConfiguration extends AbstractDynamoDbConfiguration {

		@Override
		protected List<?> customConverters() {
			return List.of(new StringBuilderToStringConverter());
		}
	}

	static class StringBuilderToStringConverter implements Converter<StringBuilder, String> {

		@Override
		public String convert(StringBuilder source) {
			return source.toString();
		}
	}
}
