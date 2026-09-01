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
package io.awspring.spring.data.dynamodb.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.core.converter.DynamoDbConversions;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

@DisplayName("AbstractDynamoDbConfiguration — custom conversions")
class AbstractDynamoDbConfigurationCustomConversionsTest {

	@Nested
	@DisplayName("Default configuration")
	class DefaultConfiguration {

		private final TestConfiguration configuration = new TestConfiguration();

		@Test
		@DisplayName("registers no custom converters and provides a non-null DynamoDbConversions bean")
		void customConverters_default_emptyListAndNonNullConversions() {

			// Act
			List<?> converters = configuration.customConverters();
			DynamoDbConversions conversions = configuration.customConversions();

			// Assert
			assertAll(() -> assertTrue(converters.isEmpty(), "customConverters() should return an empty list"),
					() -> assertNotNull(conversions, "customConversions() should not be null"));
		}

		@Test
		@DisplayName("does not predeclare a DynamoDbClient bean")
		void dynamoDbClientIsLeftToTheApplicationOrExternalAutoConfiguration() throws NoSuchMethodException {
			assertTrue(!AbstractDynamoDbConfiguration.class.getMethod("dynamoDbClient")
					.isAnnotationPresent(org.springframework.context.annotation.Bean.class));
		}
	}

	@Nested
	@DisplayName("Subclass contributing converters")
	class SubclassWithCustomConverters {

		private final CustomConvertersConfiguration configuration = new CustomConvertersConfiguration();

		@Test
		@DisplayName("exposes contributed converter and DynamoDbConversions recognises its write target")
		void customConverters_withStringBuilderConverter_registeredAndRecognised() {

			// Act
			List<?> converters = configuration.customConverters();
			DynamoDbConversions conversions = configuration.customConversions();

			// Assert
			assertAll(
					() -> assertEquals(1, converters.size(), "customConverters() should contain exactly one converter"),
					() -> assertTrue(conversions.hasCustomWriteTarget(StringBuilder.class, String.class),
							"DynamoDbConversions should recognise StringBuilder->String write target"));
		}
	}

	@Nested
	@DisplayName("Numeric simple types")
	class NumericSimpleTypes {

		private final DynamoDbConversions conversions = new DynamoDbConversions(List.of());

		@Test
		@DisplayName("treats BigDecimal, BigInteger and JDK number wrappers as simple types")
		void numberSubtypes_areSimpleTypes() {

			var simpleTypeHolder = conversions.getSimpleTypeHolder();

			assertAll(
					() -> assertTrue(simpleTypeHolder.isSimpleType(java.math.BigDecimal.class),
							"BigDecimal should be a simple type"),
					() -> assertTrue(simpleTypeHolder.isSimpleType(java.math.BigInteger.class),
							"BigInteger should be a simple type"),
					() -> assertTrue(simpleTypeHolder.isSimpleType(Integer.class), "Integer should be a simple type"),
					() -> assertTrue(simpleTypeHolder.isSimpleType(Long.class), "Long should be a simple type"));
		}
	}

	// --- Test fixtures ---

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
