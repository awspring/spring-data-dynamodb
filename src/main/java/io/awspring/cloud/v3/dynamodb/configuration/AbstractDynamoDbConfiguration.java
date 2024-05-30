package io.awspring.cloud.v3.dynamodb.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.v3.dynamodb.core.converter.DynamoDbConversions;
import io.awspring.cloud.v3.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.cloud.v3.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbMappingContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.convert.CustomConversions;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@Configuration
public abstract class AbstractDynamoDbConfiguration implements BeanClassLoaderAware {

	private @Nullable ClassLoader beanClassLoader;
	private @Nullable BeanFactory beanFactory;

	@Bean
	public DynamoDbConverter dynamoDbConverter(Optional<ObjectMapper> objectMapper) {
		ObjectMapper obj = objectMapper.orElse(new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
		obj.findAndRegisterModules();
		MappingDynamoDbConverter converter =
			new MappingDynamoDbConverter(requireBeanOfType(DynamoDbMappingContext.class), obj);
		converter.setCustomConversions(requireBeanOfType(DynamoDbConversions.class));

		return converter;
	}

	@Bean
	public DynamoDbMappingContext dynamoDbMapping() throws ClassNotFoundException {


		DynamoDbMappingContext mappingContext =
			new DynamoDbMappingContext();

		CustomConversions customConversions = requireBeanOfType(DynamoDbConversions.class);

		getBeanClassLoader().ifPresent(mappingContext::setBeanClassLoader);
		mappingContext.setInitialEntitySet(getInitialEntitySet());
		mappingContext.setSimpleTypeHolder(customConversions.getSimpleTypeHolder());

		return mappingContext;
	}

	@Bean
	public DynamoDbConversions customConversions() {
		return new DynamoDbConversions(Collections.emptyList());
	}

	@Override
	public void setBeanClassLoader(@Nullable ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	protected <T> T requireBeanOfType(@NonNull Class<T> beanType) {
		return getBeanFactory().getBean(beanType);
	}

	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	protected BeanFactory getBeanFactory() {

		Assert.state(this.beanFactory != null, "BeanFactory not initialized");

		return this.beanFactory;
	}

	protected Optional<ClassLoader> getBeanClassLoader() {
		return Optional.ofNullable(this.beanClassLoader);
	}

	protected Set<Class<?>> getInitialEntitySet() throws ClassNotFoundException {
		return DynamoDbEntityClassScanner.scan(getEntityBasePackages());
	}

	protected String[] getEntityBasePackages() {
		return new String[] {getClass().getPackage().getName()};
	}


}
