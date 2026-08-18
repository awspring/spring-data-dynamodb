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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.converter.Converter;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@DisplayName("DynamoDb converters -- scalar type conversions")
class DynamoDbConvertersTest {

	@Test
	void dateRoundTripsThroughIso8601() {
		Date original = Date.from(Instant.parse("2024-06-15T10:30:45Z"));

		AttributeValue av = DynamoDbConverters.DateToAttributeValue.INSTANCE.convert(original);
		assertNotNull(av.s());
		assertEquals("2024-06-15T10:30:45Z", av.s());

		Date restored = DynamoDbConverters.AttributeValueToDate.INSTANCE.convert(av);
		assertEquals(original.toInstant(), restored.toInstant());
	}

	@Test
	void dateWrittenInIsoFormatIsParseableByInstant() {
		Date now = new Date();
		AttributeValue av = DynamoDbConverters.DateToAttributeValue.INSTANCE.convert(now);
		Instant parsed = Instant.parse(av.s());
		assertEquals(now.toInstant(), parsed);
	}

	@Test
	void attributeValueToDateWithNonStringValueThrowsConversionFailure() {
		AttributeValue numberOnly = AttributeValue.builder().n("12345").build();
		assertThrows(ConversionFailedException.class,
				() -> DynamoDbConverters.AttributeValueToDate.INSTANCE.convert(numberOnly));
	}

	@Test
	void attributeValueToDateWithNonIso8601StringThrowsConversionFailure() {
		AttributeValue badFormat = AttributeValue.builder().s("Fri Aug 07 19:47:16 CEST 2026").build();
		assertThrows(ConversionFailedException.class,
				() -> DynamoDbConverters.AttributeValueToDate.INSTANCE.convert(badFormat));
	}

	@Test
	void attributeValueToStringIsRegisteredExactlyOnce() {
		Collection<Object> converters = DynamoDbConverters.getConvertersToRegister();
		long count = converters.stream().filter(c -> c == DynamoDbConverters.AttributeValueToString.INSTANCE).count();
		assertEquals(1, count, "AttributeValueToString must be registered exactly once");
	}

	@Test
	void allExpectedConvertersAreRegistered() {
		Collection<Object> converters = DynamoDbConverters.getConvertersToRegister();
		assertTrue(converters.contains(DynamoDbConverters.StringToAttributeValue.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.AttributeValueToString.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.UuidToAttributeValue.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.AttributeValueToUuid.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.BooleanToAttributeValue.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.AttributeValueToBoolean.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.NumberToAttributeValue.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.AttributeValueToNumber.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.URLToAttributeValue.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.AttributeValueToURLConverter.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.DateToAttributeValue.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.AttributeValueToDate.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.LocalDateToAttributeValue.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.AttributeValueToLocalDate.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.LocalDateTimeToAttributeValue.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.AttributeValueToLocalDateTime.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.InstantToAttributeValue.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.AttributeValueToInstant.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.ZoneIdToAttributeValue.INSTANCE));
		assertTrue(converters.contains(DynamoDbConverters.AttributeValueToZoneId.INSTANCE));
	}

	@Test
	void attributeValueToBooleanWithBoolTypeWorks() {
		AttributeValue trueBool = AttributeValue.builder().bool(true).build();
		AttributeValue falseBool = AttributeValue.builder().bool(false).build();

		assertEquals(Boolean.TRUE, DynamoDbConverters.AttributeValueToBoolean.INSTANCE.convert(trueBool));
		assertEquals(Boolean.FALSE, DynamoDbConverters.AttributeValueToBoolean.INSTANCE.convert(falseBool));
	}

	@Test
	void attributeValueToBooleanParsesStringTrueOrFalseCaseInsensitively() {
		assertEquals(Boolean.TRUE, DynamoDbConverters.AttributeValueToBoolean.INSTANCE
				.convert(AttributeValue.builder().s("true").build()));
		assertEquals(Boolean.TRUE, DynamoDbConverters.AttributeValueToBoolean.INSTANCE
				.convert(AttributeValue.builder().s("TRUE").build()));
		assertEquals(Boolean.FALSE, DynamoDbConverters.AttributeValueToBoolean.INSTANCE
				.convert(AttributeValue.builder().s("false").build()));
		assertEquals(Boolean.FALSE, DynamoDbConverters.AttributeValueToBoolean.INSTANCE
				.convert(AttributeValue.builder().s("False").build()));
	}

	@Test
	void attributeValueToBooleanWithUnparseableValueThrowsConversionFailure() {
		AttributeValue nonsense = AttributeValue.builder().n("42").build();
		assertThrows(ConversionFailedException.class,
				() -> DynamoDbConverters.AttributeValueToBoolean.INSTANCE.convert(nonsense));

		AttributeValue nonsenseString = AttributeValue.builder().s("yes").build();
		assertThrows(ConversionFailedException.class,
				() -> DynamoDbConverters.AttributeValueToBoolean.INSTANCE.convert(nonsenseString));
	}

	@Test
	void attributeValueToUuidWithNonStringValueThrowsConversionFailure() {
		AttributeValue numeric = AttributeValue.builder().n("123").build();
		assertThrows(ConversionFailedException.class,
				() -> DynamoDbConverters.AttributeValueToUuid.INSTANCE.convert(numeric));

		AttributeValue boolAv = AttributeValue.builder().bool(true).build();
		assertThrows(ConversionFailedException.class,
				() -> DynamoDbConverters.AttributeValueToUuid.INSTANCE.convert(boolAv));
	}

	@Test
	void attributeValueToUuidWithInvalidStringThrowsConversionFailure() {
		AttributeValue malformed = AttributeValue.builder().s("not-a-uuid").build();
		assertThrows(ConversionFailedException.class,
				() -> DynamoDbConverters.AttributeValueToUuid.INSTANCE.convert(malformed));
	}

	@Test
	void uuidRoundTrips() {
		UUID original = UUID.randomUUID();
		AttributeValue av = DynamoDbConverters.UuidToAttributeValue.INSTANCE.convert(original);
		UUID restored = DynamoDbConverters.AttributeValueToUuid.INSTANCE.convert(av);
		assertEquals(original, restored);
	}

	@Test
	void urlConverterUsesUriBasedConstructionAndParsesHttps() throws Exception {
		URL original = URI.create("https://example.com/path?q=1").toURL();
		AttributeValue av = DynamoDbConverters.URLToAttributeValue.INSTANCE.convert(original);
		URL restored = DynamoDbConverters.AttributeValueToURLConverter.INSTANCE.convert(av);
		assertEquals(original, restored);
	}

	@Test
	void urlConverterWithNonStringValueThrowsConversionFailure() {
		AttributeValue numeric = AttributeValue.builder().n("100").build();
		assertThrows(ConversionFailedException.class,
				() -> DynamoDbConverters.AttributeValueToURLConverter.INSTANCE.convert(numeric));
	}

	@Test
	void urlConverterWithMalformedUrlThrowsConversionFailure() {
		AttributeValue malformed = AttributeValue.builder().s("not a url with spaces").build();
		assertThrows(ConversionFailedException.class,
				() -> DynamoDbConverters.AttributeValueToURLConverter.INSTANCE.convert(malformed));
	}

	@Test
	void numberConverterFactoryThrowsClearErrorForNonNumericAttributeValue() {
		Converter<AttributeValue, Integer> integerConverter = DynamoDbConverters.AttributeValueToNumber.INSTANCE
				.getConverter(Integer.class);

		AttributeValue stringAv = AttributeValue.builder().s("hello").build();
		assertThrows(ConversionFailedException.class, () -> integerConverter.convert(stringAv));
	}

	@Test
	void numberConverterFactoryReturnsCorrectTypedNumber() {
		Converter<AttributeValue, Integer> integerConverter = DynamoDbConverters.AttributeValueToNumber.INSTANCE
				.getConverter(Integer.class);
		Converter<AttributeValue, Long> longConverter = DynamoDbConverters.AttributeValueToNumber.INSTANCE
				.getConverter(Long.class);

		AttributeValue av = AttributeValue.builder().n("42").build();

		assertEquals(Integer.valueOf(42), integerConverter.convert(av));
		assertEquals(Long.valueOf(42L), longConverter.convert(av));
	}

	@Test
	void instantConverterRoundTrips() {
		Instant original = Instant.parse("2024-01-01T00:00:00Z");
		AttributeValue av = DynamoDbConverters.InstantToAttributeValue.INSTANCE.convert(original);
		Instant restored = DynamoDbConverters.AttributeValueToInstant.INSTANCE.convert(av);
		assertEquals(original, restored);
	}

	@Test
	void attributeValueToInstantWithNonStringThrowsConversionFailure() {
		AttributeValue numeric = AttributeValue.builder().n("12345").build();
		assertThrows(ConversionFailedException.class,
				() -> DynamoDbConverters.AttributeValueToInstant.INSTANCE.convert(numeric));
	}

	@Test
	void localDateConverterRoundTrips() {
		LocalDate original = LocalDate.of(2024, 3, 15);
		AttributeValue av = DynamoDbConverters.LocalDateToAttributeValue.INSTANCE.convert(original);
		LocalDate restored = DynamoDbConverters.AttributeValueToLocalDate.INSTANCE.convert(av);
		assertEquals(original, restored);
	}

	@Test
	void localDateWithNonStringValueThrowsConversionFailure() {
		AttributeValue numeric = AttributeValue.builder().n("20240315").build();
		assertThrows(ConversionFailedException.class,
				() -> DynamoDbConverters.AttributeValueToLocalDate.INSTANCE.convert(numeric));
	}

	@Test
	void localDateTimeConverterRoundTrips() {
		LocalDateTime original = LocalDateTime.of(2024, 3, 15, 12, 30, 45);
		AttributeValue av = DynamoDbConverters.LocalDateTimeToAttributeValue.INSTANCE.convert(original);
		LocalDateTime restored = DynamoDbConverters.AttributeValueToLocalDateTime.INSTANCE.convert(av);
		assertEquals(original, restored);
	}

	@Test
	void localDateTimeWithNonStringValueThrowsConversionFailure() {
		AttributeValue numeric = AttributeValue.builder().n("42").build();
		assertThrows(ConversionFailedException.class,
				() -> DynamoDbConverters.AttributeValueToLocalDateTime.INSTANCE.convert(numeric));
	}

	@Test
	void zoneIdConverterRoundTrips() {
		ZoneId original = ZoneId.of("America/Los_Angeles");
		AttributeValue av = DynamoDbConverters.ZoneIdToAttributeValue.INSTANCE.convert(original);
		ZoneId restored = DynamoDbConverters.AttributeValueToZoneId.INSTANCE.convert(av);
		assertEquals(original, restored);
	}

	@Test
	void zoneIdWithNonStringValueThrowsConversionFailure() {
		AttributeValue numeric = AttributeValue.builder().n("0").build();
		assertThrows(ConversionFailedException.class,
				() -> DynamoDbConverters.AttributeValueToZoneId.INSTANCE.convert(numeric));
	}
}
