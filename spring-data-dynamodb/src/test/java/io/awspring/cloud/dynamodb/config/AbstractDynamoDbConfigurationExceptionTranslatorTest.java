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
import static org.mockito.Mockito.mock;

import io.awspring.cloud.dynamodb.core.DefaultDynamoDbExceptionTranslator;
import io.awspring.cloud.dynamodb.core.DynamoDbExceptionTranslator;
import io.awspring.cloud.dynamodb.core.DynamoDbTemplate;
import io.awspring.cloud.dynamodb.core.converter.DynamoDbConverter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessException;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class AbstractDynamoDbConfigurationExceptionTranslatorTest {

	@Test
	void defaultConfigurationUsesDefaultExceptionTranslator() {

		TestConfiguration configuration = new TestConfiguration();

		DynamoDbExceptionTranslator translator = configuration.dynamoDbExceptionTranslator();
		DynamoDbTemplate template = configuration.dynamoDbTemplate(mock(DynamoDbClient.class),
				Mockito.mock(DynamoDbConverter.class), translator);

		assertThat(translator).isInstanceOf(DefaultDynamoDbExceptionTranslator.class);
		assertThat(template.getExceptionTranslator()).isSameAs(translator);
	}

	@Test
	void subclassCanOverrideExceptionTranslator() {

		RuntimeException marker = new RuntimeException("boom");
		DynamoDbExceptionTranslator custom = ex -> new CustomTranslatedException(ex);

		CustomTranslatorConfiguration configuration = new CustomTranslatorConfiguration(custom);
		DynamoDbTemplate template = configuration.dynamoDbTemplate(mock(DynamoDbClient.class),
				mock(DynamoDbConverter.class), configuration.dynamoDbExceptionTranslator());

		assertThat(template.getExceptionTranslator()).isSameAs(custom);
		assertThat(template.getExceptionTranslator().translate("task", null, marker))
				.isInstanceOf(CustomTranslatedException.class).hasCause(marker);
	}

	static class TestConfiguration extends AbstractDynamoDbConfiguration {
	}

	static class CustomTranslatorConfiguration extends AbstractDynamoDbConfiguration {

		private final DynamoDbExceptionTranslator translator;

		CustomTranslatorConfiguration(DynamoDbExceptionTranslator translator) {
			this.translator = translator;
		}

		@Override
		public DynamoDbExceptionTranslator dynamoDbExceptionTranslator() {
			return this.translator;
		}
	}

	static class CustomTranslatedException extends DataAccessException {

		CustomTranslatedException(@Nullable Throwable cause) {
			super("custom translation", cause);
		}
	}
}
