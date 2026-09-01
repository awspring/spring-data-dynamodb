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
package io.awspring.spring.data.dynamodb.core.converter;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.util.Assert;
import org.springframework.util.NumberUtils;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbConverters {

	static Collection<Object> getConvertersToRegister() {

		List<Object> converters = new ArrayList<>();

		converters.add(StringToAttributeValue.INSTANCE);
		converters.add(AttributeValueToString.INSTANCE);
		converters.add(UuidToAttributeValue.INSTANCE);
		converters.add(AttributeValueToUuid.INSTANCE);
		converters.add(AttributeValueToNumber.INSTANCE);
		converters.add(NumberToAttributeValue.INSTANCE);
		converters.add(URLToAttributeValue.INSTANCE);
		converters.add(AttributeValueToURLConverter.INSTANCE);
		converters.add(DateToAttributeValue.INSTANCE);
		converters.add(LocalDateToAttributeValue.INSTANCE);
		converters.add(LocalDateTimeToAttributeValue.INSTANCE);
		converters.add(ZoneIdToAttributeValue.INSTANCE);
		converters.add(InstantToAttributeValue.INSTANCE);
		converters.add(BooleanToAttributeValue.INSTANCE);
		converters.add(AttributeValueToBoolean.INSTANCE);
		converters.add(AttributeValueToLocalDateTime.INSTANCE);
		converters.add(AttributeValueToLocalDate.INSTANCE);
		converters.add(AttributeValueToZoneId.INSTANCE);
		converters.add(AttributeValueToInstant.INSTANCE);
		converters.add(AttributeValueToDate.INSTANCE);
		return converters;
	}

	private static final TypeDescriptor ATTRIBUTE_VALUE = TypeDescriptor.valueOf(AttributeValue.class);

	private static <T> ConversionFailedException conversionFailure(AttributeValue source, Class<T> targetType,
			String reason) {
		return new ConversionFailedException(ATTRIBUTE_VALUE, TypeDescriptor.valueOf(targetType), source,
				new IllegalArgumentException(reason));
	}

	enum StringToAttributeValue implements Converter<String, AttributeValue> {
		INSTANCE;

		public AttributeValue convert(String source) {
			return AttributeValue.builder().s(source).build();
		}
	}

	enum UuidToAttributeValue implements Converter<UUID, AttributeValue> {
		INSTANCE;

		public AttributeValue convert(UUID source) {
			return AttributeValue.builder().s(source.toString()).build();
		}
	}

	enum AttributeValueToUuid implements Converter<AttributeValue, UUID> {
		INSTANCE;

		public UUID convert(AttributeValue source) {
			if (source.s() == null) {
				throw conversionFailure(source, UUID.class,
						"AttributeValue does not carry a String (S) value; UUID requires a String-typed value");
			}
			try {
				return UUID.fromString(source.s());
			}
			catch (IllegalArgumentException e) {
				throw new ConversionFailedException(ATTRIBUTE_VALUE, TypeDescriptor.valueOf(UUID.class), source, e);
			}
		}
	}

	enum AttributeValueToString implements Converter<AttributeValue, String> {
		INSTANCE;

		public String convert(AttributeValue source) {
			return source.s();
		}
	}

	enum BooleanToAttributeValue implements Converter<Boolean, AttributeValue> {
		INSTANCE;

		public AttributeValue convert(Boolean source) {
			return AttributeValue.builder().bool(source).build();
		}
	}

	enum AttributeValueToBoolean implements Converter<AttributeValue, Boolean> {
		INSTANCE;

		public Boolean convert(AttributeValue source) {
			if (source.bool() != null) {
				return source.bool();
			}
			if (source.s() != null) {
				String s = source.s();
				if ("true".equalsIgnoreCase(s)) {
					return Boolean.TRUE;
				}
				if ("false".equalsIgnoreCase(s)) {
					return Boolean.FALSE;
				}
			}
			throw conversionFailure(source, Boolean.class,
					"AttributeValue does not carry a Boolean (BOOL) value and could not be parsed from a String");
		}
	}

	public enum AttributeValueToNumber implements ConverterFactory<AttributeValue, Number> {

		INSTANCE;

		@Override
		public <T extends Number> Converter<AttributeValue, T> getConverter(Class<T> targetType) {
			Assert.notNull(targetType, "Target type must not be null");
			return new AttributeValueToNumberConverter<>(targetType);
		}

		private static final class AttributeValueToNumberConverter<T extends Number>
				implements Converter<AttributeValue, T> {

			private final Class<T> targetType;

			AttributeValueToNumberConverter(Class<T> targetType) {
				this.targetType = targetType;
			}

			@Override
			public T convert(AttributeValue source) {
				String object = source.n();
				if (object == null) {
					throw conversionFailure(source, this.targetType,
							"AttributeValue does not carry a Number (N) value; " + this.targetType.getSimpleName()
									+ " requires a Number-typed value");
				}
				return NumberUtils.parseNumber(object, this.targetType);
			}
		}
	}

	public enum NumberToAttributeValue implements Converter<Number, AttributeValue> {

		INSTANCE;

		public AttributeValue convert(Number source) {
			return AttributeValue.builder().n(source.toString()).build();
		}
	}

	enum URLToAttributeValue implements Converter<URL, AttributeValue> {
		INSTANCE;

		public AttributeValue convert(URL source) {
			return AttributeValue.builder().s(source.toString()).build();
		}
	}

	enum AttributeValueToURLConverter implements Converter<AttributeValue, URL> {
		INSTANCE;

		private static final TypeDescriptor SOURCE = TypeDescriptor.valueOf(AttributeValue.class);
		private static final TypeDescriptor TARGET = TypeDescriptor.valueOf(URL.class);

		public URL convert(AttributeValue source) {
			if (source.s() == null) {
				throw new ConversionFailedException(SOURCE, TARGET, source, new IllegalArgumentException(
						"AttributeValue does not carry a String (S) value; URL requires " + "a String-typed value"));
			}
			try {
				return new URI(source.s()).toURL();
			}
			catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
				throw new ConversionFailedException(SOURCE, TARGET, source, e);
			}
		}
	}

	public enum DateToAttributeValue implements Converter<Date, AttributeValue> {

		INSTANCE;

		public AttributeValue convert(Date source) {
			return AttributeValue.builder().s(source.toInstant().toString()).build();
		}
	}

	public enum LocalDateToAttributeValue implements Converter<LocalDate, AttributeValue> {

		INSTANCE;

		public AttributeValue convert(LocalDate source) {
			return AttributeValue.builder().s(source.toString()).build();
		}
	}

	public enum LocalDateTimeToAttributeValue implements Converter<LocalDateTime, AttributeValue> {

		INSTANCE;

		public AttributeValue convert(LocalDateTime source) {
			return AttributeValue.builder().s(source.toString()).build();
		}
	}

	public enum AttributeValueToDate implements Converter<AttributeValue, Date> {

		INSTANCE;

		public Date convert(AttributeValue source) {
			if (source.s() == null) {
				throw conversionFailure(source, Date.class,
						"AttributeValue does not carry a String (S) value; Date requires a String-typed ISO-8601 value");
			}
			try {
				return Date.from(Instant.parse(source.s()));
			}
			catch (java.time.format.DateTimeParseException e) {
				throw new ConversionFailedException(ATTRIBUTE_VALUE, TypeDescriptor.valueOf(Date.class), source, e);
			}
		}
	}

	public enum AttributeValueToLocalDate implements Converter<AttributeValue, LocalDate> {

		INSTANCE;

		public LocalDate convert(AttributeValue source) {
			if (source.s() == null) {
				throw conversionFailure(source, LocalDate.class,
						"AttributeValue does not carry a String (S) value; LocalDate requires a String-typed value");
			}
			return LocalDate.parse(source.s());
		}
	}

	public enum AttributeValueToLocalDateTime implements Converter<AttributeValue, LocalDateTime> {

		INSTANCE;

		public LocalDateTime convert(AttributeValue source) {
			if (source.s() == null) {
				throw conversionFailure(source, LocalDateTime.class,
						"AttributeValue does not carry a String (S) value; LocalDateTime requires a String-typed value");
			}
			return LocalDateTime.parse(source.s());
		}
	}

	public enum ZoneIdToAttributeValue implements Converter<ZoneId, AttributeValue> {

		INSTANCE;

		public AttributeValue convert(ZoneId source) {
			return AttributeValue.builder().s(source.toString()).build();
		}
	}

	public enum InstantToAttributeValue implements Converter<Instant, AttributeValue> {

		INSTANCE;

		public AttributeValue convert(Instant source) {
			return AttributeValue.builder().s(source.toString()).build();
		}
	}

	public enum AttributeValueToZoneId implements Converter<AttributeValue, ZoneId> {

		INSTANCE;

		public ZoneId convert(AttributeValue source) {
			if (source.s() == null) {
				throw conversionFailure(source, ZoneId.class,
						"AttributeValue does not carry a String (S) value; ZoneId requires a String-typed value");
			}
			return ZoneId.of(source.s());
		}
	}

	public enum AttributeValueToInstant implements Converter<AttributeValue, Instant> {

		INSTANCE;

		public Instant convert(AttributeValue source) {
			if (source.s() == null) {
				throw conversionFailure(source, Instant.class,
						"AttributeValue does not carry a String (S) value; Instant requires a String-typed value");
			}
			return Instant.parse(source.s());
		}
	}

}
