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

import io.awspring.spring.data.dynamodb.core.DefaultDynamoDbExceptionTranslator;
import io.awspring.spring.data.dynamodb.core.DynamoDbExceptionTranslator;
import io.awspring.spring.data.dynamodb.core.DynamoDbTemplate;
import io.awspring.spring.data.dynamodb.core.converter.DynamoDbConversions;
import io.awspring.spring.data.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.convert.PropertyValueConversions;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Base Spring configuration for DynamoDB mapping and template beans.
 *
 * @author Matej Nedic
 * @since 1.0.0
 */
@Configuration
public abstract class AbstractDynamoDbConfiguration implements BeanClassLoaderAware, BeanFactoryAware {

	private @Nullable ClassLoader beanClassLoader;
	private @Nullable BeanFactory beanFactory;

	/** @return the entity converter */
	@Bean
	public DynamoDbConverter dynamoDbConverter(DynamoDbMappingContext dynamoDbMappingContext,
			DynamoDbConversions dynamoDbConversions, PropertyValueConversions propertyValueConversions) {
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(dynamoDbMappingContext);
		converter.setCustomConversions(dynamoDbConversions);
		converter.setPropertyValueConversions(propertyValueConversions);
		converter.afterPropertiesSet();

		return converter;
	}

	/** @return the default DynamoDB exception translator */
	@Bean
	public DynamoDbExceptionTranslator dynamoDbExceptionTranslator() {
		return new DefaultDynamoDbExceptionTranslator();
	}

	/** @return the configured DynamoDB template */
	@Bean
	public DynamoDbTemplate dynamoDbTemplate(DynamoDbClient dynamoDbClient, DynamoDbConverter dynamoDbConverter,
			DynamoDbExceptionTranslator dynamoDbExceptionTranslator) {
		DynamoDbTemplate template = new DynamoDbTemplate(dynamoDbClient, dynamoDbConverter);
		template.setExceptionTranslator(dynamoDbExceptionTranslator);
		return template;
	}

	/** @return the AWS SDK DynamoDB client; override to configure region and credentials */
	@Bean
	public DynamoDbClient dynamoDbClient() {
		return DynamoDbClient.builder().build();
	}

	/** @return the configured mapping context */
	@Bean
	public DynamoDbMappingContext dynamoDbMappingContext(DynamoDbConversions dynamoDbConversions)
			throws ClassNotFoundException {

		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();

		getBeanClassLoader().ifPresent(mappingContext::setBeanClassLoader);
		mappingContext.setInitialEntitySet(getInitialEntitySet());
		mappingContext.setSimpleTypeHolder(dynamoDbConversions.getSimpleTypeHolder());

		return mappingContext;
	}

	/** @return custom converters added to the mapping conversion service */
	protected List<?> customConverters() {
		return Collections.emptyList();
	}

	/** @return the configured DynamoDB custom conversions */
	@Bean
	public DynamoDbConversions customConversions() {
		return new DynamoDbConversions(customConverters());
	}

	/** @return per-property value conversions */
	@Bean
	public PropertyValueConversions propertyValueConversions() {
		return PropertyValueConversions.simple(registrar -> {
		});
	}

	@Override
	public void setBeanClassLoader(@Nullable ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	/** Returns a required bean from this configuration's bean factory. */
	protected <T> T requireBeanOfType(@NonNull Class<T> beanType) {
		return getBeanFactory().getBean(beanType);
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	/** @return the initialized bean factory */
	protected @NonNull BeanFactory getBeanFactory() {

		Assert.state(this.beanFactory != null, "BeanFactory not initialized");

		return this.beanFactory;
	}

	/** @return the configured bean class loader, if available */
	protected Optional<ClassLoader> getBeanClassLoader() {
		return Optional.ofNullable(this.beanClassLoader);
	}

	/** @return entity classes discovered in the configured base packages */
	protected Set<Class<?>> getInitialEntitySet() throws ClassNotFoundException {
		return DynamoDbEntityClassScanner.scan(getEntityBasePackages());
	}

	/** @return packages scanned for mapped entity classes */
	protected String[] getEntityBasePackages() {
		return new String[] { getClass().getPackage().getName() };
	}

}
