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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import io.awspring.cloud.dynamodb.core.DefaultDynamoDbExceptionTranslator;
import io.awspring.cloud.dynamodb.core.DynamoDbExceptionTranslator;
import io.awspring.cloud.dynamodb.core.DynamoDbTemplate;
import io.awspring.cloud.dynamodb.core.converter.DynamoDbConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@DisplayName("AbstractDynamoDbConfiguration — exception translator wiring")
class AbstractDynamoDbConfigurationExceptionTranslatorTest {

	private static final String TRANSLATE_TASK = "test-task";

	@Nested
	@DisplayName("Default configuration")
	class DefaultConfiguration {

		private final TestConfiguration configuration = new TestConfiguration();

		@Test
		@DisplayName("uses DefaultDynamoDbExceptionTranslator and wires it into the template")
		void dynamoDbExceptionTranslator_default_defaultInstanceWiredIntoTemplate() {

			// Arrange
			DynamoDbExceptionTranslator translator = configuration.dynamoDbExceptionTranslator();
			DynamoDbTemplate template = configuration.dynamoDbTemplate(mock(DynamoDbClient.class),
					mock(DynamoDbConverter.class), translator);

			// Assert
			assertAll(
					() -> assertInstanceOf(DefaultDynamoDbExceptionTranslator.class, translator,
							"default translator should be DefaultDynamoDbExceptionTranslator"),
					() -> assertSame(translator, template.getExceptionTranslator(),
							"template should use the exact translator instance"));
		}
	}

	@Nested
	@DisplayName("Subclass with custom translator")
	class CustomTranslatorOverride {

		@Test
		@DisplayName("template uses the overridden translator and it translates correctly")
		void dynamoDbExceptionTranslator_customOverride_usedByTemplateAndTranslatesCorrectly() {

			// Arrange
			RuntimeException marker = new RuntimeException("boom");
			DynamoDbExceptionTranslator custom = ex -> new CustomTranslatedException(ex);
			CustomTranslatorConfiguration configuration = new CustomTranslatorConfiguration(custom);

			// Act
			DynamoDbTemplate template = configuration.dynamoDbTemplate(mock(DynamoDbClient.class),
					mock(DynamoDbConverter.class), configuration.dynamoDbExceptionTranslator());

			// Assert
			DataAccessException translated = template.getExceptionTranslator().translate(TRANSLATE_TASK, null, marker);

			assertAll(
					() -> assertSame(custom, template.getExceptionTranslator(),
							"template should use the custom translator instance"),
					() -> assertInstanceOf(CustomTranslatedException.class, translated,
							"translated exception should be CustomTranslatedException"),
					() -> assertSame(marker, translated.getCause(),
							"translated exception should wrap the original cause"));
		}
	}

	// --- Test fixtures ---

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
