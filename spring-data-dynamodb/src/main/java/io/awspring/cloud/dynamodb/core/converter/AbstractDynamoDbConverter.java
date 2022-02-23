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

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.convert.PropertyValueConversions;
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.convert.ValueConversionContext;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.model.EntityInstantiators;
import org.springframework.util.Assert;

public abstract class AbstractDynamoDbConverter implements DynamoDbConverter, InitializingBean {

	protected final ConversionService conversionService;
	protected CustomConversions conversions = new DynamoDbConversions();
	protected EntityInstantiators instantiators = new EntityInstantiators();

	protected PropertyValueConversions propertyValueConversions = NoOpPropertyValueConversions.INSTANCE;

	protected AbstractDynamoDbConverter(ConversionService conversionService) {

		Assert.notNull(conversionService, "ConversionService must not be null");

		this.conversionService = conversionService;
	}

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

	public void setCustomConversions(CustomConversions conversions) {
		this.conversions = conversions;
	}

	public void setPropertyValueConversions(@Nullable PropertyValueConversions propertyValueConversions) {
		this.propertyValueConversions = propertyValueConversions == null ? NoOpPropertyValueConversions.INSTANCE
				: propertyValueConversions;
	}

	public PropertyValueConversions getPropertyValueConversions() {
		return this.propertyValueConversions;
	}

	enum NoOpPropertyValueConversions implements PropertyValueConversions {

		INSTANCE;

		@Override
		public boolean hasValueConverter(PersistentProperty<?> property) {
			return false;
		}

		@Override
		public <DV, SV, P extends PersistentProperty<P>, VCC extends ValueConversionContext<P>> PropertyValueConverter<DV, SV, VCC> getValueConverter(
				P property) {
			throw new UnsupportedOperationException(
					"No PropertyValueConverter registered; hasValueConverter is always false for " + property);
		}
	}
}
