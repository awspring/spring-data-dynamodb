package io.awspring.cloud.v3.dynamodb.core.converter;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.util.Assert;
import org.springframework.util.NumberUtils;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Wrapper class to contain useful converters for the usage with DynamoDb.
 *
 * @author Matej Nedic
 * @since 3.0
 */
public class DynamoDbConverters {

	static Collection<Object> getConvertersToRegister() {

		List<Object> converters = new ArrayList<>();

		converters.add(StringToAttributeValue.INSTANCE);
		converters.add(AttributeValueToString.INSTANCE);
		converters.add(UuidToAttributeValue.INSTANCE);
		converters.add(AttributeValueToUuid	.INSTANCE);
		converters.add(AttributeValueToNumber.INSTANCE);
		converters.add(NumberToAttributeValue.INSTANCE);
		converters.add(URLToAttributeValue.INSTANCE);
		converters.add(AttributeValueToURLConverter.INSTANCE);
		converters.add(DateToAttributeValue.INSTANCE);
		converters.add(LocalDateToAttributeValue.INSTANCE);
		converters.add(LocalDateTimeToAttributeValue.INSTANCE);
		converters.add(ZoneIdToAttributeValue.INSTANCE);
		converters.add(AttributeValueToString.INSTANCE);
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
			return UUID.fromString(source.s());
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
			return source.bool();
		}
	}


	public enum AttributeValueToNumber
			implements ConverterFactory<AttributeValue, Number> {

		INSTANCE;

		@Override
		public <T extends Number> Converter<AttributeValue, T> getConverter(
				Class<T> targetType) {
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

				return (object != null ? NumberUtils.parseNumber(object, this.targetType)
						: null);
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

		private static final TypeDescriptor SOURCE = TypeDescriptor
				.valueOf(AttributeValue.class);
		private static final TypeDescriptor TARGET = TypeDescriptor.valueOf(URL.class);

		public URL convert(AttributeValue source) {

			try {
				return new URL(source.s());
			}
			catch (MalformedURLException e) {
				throw new ConversionFailedException(SOURCE, TARGET, source, e);
			}
		}
	}

	public enum DateToAttributeValue implements Converter<Date, AttributeValue> {

		INSTANCE;

		public AttributeValue convert(Date source) {
			return AttributeValue.builder().s(source.toString()).build();
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

		public Date convert(AttributeValue source) { return Date.from(Instant.parse(source.s()));
		}
	}

	public enum AttributeValueToLocalDate implements Converter<AttributeValue, LocalDate> {

		INSTANCE;

		public LocalDate convert(AttributeValue source) {
			return LocalDate.parse(source.s());
		}
	}

	public enum AttributeValueToLocalDateTime implements Converter<AttributeValue, LocalDateTime> {

		INSTANCE;

		public LocalDateTime convert(AttributeValue source) {
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
			return ZoneId.of(source.s());
		}
	}

	public enum AttributeValueToInstant implements Converter<AttributeValue, Instant> {

		INSTANCE;

		public Instant convert(AttributeValue source) {
			return Instant.parse(source.s());
		}
	}

}
