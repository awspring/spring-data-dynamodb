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
package io.awspring.cloud.dynamodb.repository.config;

import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.DynamoDbRepository;
import io.awspring.cloud.dynamodb.repository.SimpleDynamoDbRepository;
import io.awspring.cloud.dynamodb.repository.support.DynamoDbRepositoryFactoryBean;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.data.repository.config.AnnotationRepositoryConfigurationSource;
import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport;
import org.springframework.data.repository.config.XmlRepositoryConfigurationSource;
import org.springframework.util.StringUtils;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbRepositoryConfigurationExtension extends RepositoryConfigurationExtensionSupport {

	private static final String DEFAULT_OPERATIONS_BEAN_NAME = "dynamoDbTemplate";
	private static final String OPERATIONS_REF_ATTRIBUTE = "dynamoDbOperationsRef";

	@Override
	public String getModuleName() {
		return "DynamoDB";
	}

	@Override
	protected String getModulePrefix() {
		return "dynamodb";
	}

	@Override
	public String getRepositoryBaseClassName() {
		return SimpleDynamoDbRepository.class.getName();
	}

	@Override
	public String getRepositoryFactoryBeanClassName() {
		return DynamoDbRepositoryFactoryBean.class.getName();
	}

	@Override
	public void postProcess(BeanDefinitionBuilder builder, XmlRepositoryConfigurationSource config) {
		builder.addPropertyReference("dynamoDbOperations", DEFAULT_OPERATIONS_BEAN_NAME);
	}

	@Override
	public void postProcess(BeanDefinitionBuilder builder, AnnotationRepositoryConfigurationSource config) {

		String operationsRef = config.getAttributes().containsKey(OPERATIONS_REF_ATTRIBUTE)
				? config.getAttributes().getString(OPERATIONS_REF_ATTRIBUTE)
				: null;

		builder.addPropertyReference("dynamoDbOperations",
				StringUtils.hasText(operationsRef) ? operationsRef : DEFAULT_OPERATIONS_BEAN_NAME);
	}

	@Override
	protected Collection<Class<? extends Annotation>> getIdentifyingAnnotations() {
		return Collections.singleton(Table.class);
	}

	@Override
	protected Collection<Class<?>> getIdentifyingTypes() {
		return Collections.singleton(DynamoDbRepository.class);
	}
}
