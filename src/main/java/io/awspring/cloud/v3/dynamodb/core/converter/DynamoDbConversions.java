package io.awspring.cloud.v3.dynamodb.core.converter;

import org.springframework.data.convert.CustomConversions;
import org.springframework.data.mapping.model.SimpleTypeHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DynamoDbConversions extends CustomConversions {

	private static final StoreConversions STORE_CONVERSIONS;
	private static final List<Object> STORE_CONVERTERS;

	static {
		List<Object> converters = new ArrayList<>(DynamoDbConverters.getConvertersToRegister());
		STORE_CONVERTERS = Collections.unmodifiableList(converters);

		STORE_CONVERSIONS = StoreConversions.of(SimpleTypeHolder.DEFAULT, STORE_CONVERTERS);
	}

	public DynamoDbConversions(List<?> converters) {
		super(new ConverterConfiguration(STORE_CONVERSIONS, converters));
	}
	
	public DynamoDbConversions(ConverterConfiguration converterConfiguration) {
		super(converterConfiguration);
	}

	public DynamoDbConversions(StoreConversions storeConversions, Collection<?> converters) {
		super(storeConversions, converters);
	}

	DynamoDbConversions() {
		this(Collections.emptyList());
	}
}
