package io.awspring.cloud.v3.dynamodb.core.converter;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.mapping.model.EntityInstantiators;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public abstract class AbstractDynamoDbConverter implements DynamoDbConverter, InitializingBean {

	protected final ConversionService conversionService;
	protected CustomConversions conversions = new DynamoDbConversions();
	protected EntityInstantiators instantiators = new EntityInstantiators();

	/**
	 * Create a new {@link AbstractDynamoDbConverter} using the given {@link ConversionService}.
	 */
	protected AbstractDynamoDbConverter(ConversionService conversionService) {

		Assert.notNull(conversionService, "ConversionService must not be null");

		this.conversionService = conversionService;
	}

	/**
	 * Registers {@link EntityInstantiators} to customize entity instantiation.
	 *
	 * @param instantiators can be {@literal null}. Uses default {@link EntityInstantiators} if so.
	 */
	public void setInstantiators(@Nullable EntityInstantiators instantiators) {
		this.instantiators = instantiators == null ? new EntityInstantiators() : instantiators;
	}

	private void initializeConverters() {

		ConversionService conversionService = getConversionService();

		if (conversionService instanceof GenericConversionService) {
			getCustomConversions().registerConvertersIn((GenericConversionService) conversionService);
		}
	}

	public void afterPropertiesSet() {
		initializeConverters();
	}

	@Override
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	@Override
	public CustomConversions getCustomConversions() {
		return this.conversions;
	}

	/**
	 * Registers the given custom conversions with the converter.
	 */
	public void setCustomConversions(CustomConversions conversions) {
		this.conversions = conversions;
	}
}
