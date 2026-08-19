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
package io.awspring.spring.data.dynamodb.repository.cdi;

import io.awspring.spring.data.dynamodb.core.DynamoDbOperations;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.ProcessBean;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.repository.cdi.CdiRepositoryBean;
import org.springframework.data.repository.cdi.CdiRepositoryExtensionSupport;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbRepositoryExtension extends CdiRepositoryExtensionSupport {

	private final Map<Set<Annotation>, Bean<DynamoDbOperations>> dynamoDbOperationsMap = new HashMap<>();

	<T> void processBean(@Observes ProcessBean<T> processBean) {

		Bean<T> bean = processBean.getBean();
		bean.getTypes().stream()
				.filter(type -> type instanceof Class<?> && DynamoDbOperations.class.isAssignableFrom((Class<?>) type))
				.forEach(type -> dynamoDbOperationsMap.put(bean.getQualifiers(), ((Bean<DynamoDbOperations>) bean)));
	}

	void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery, BeanManager beanManager) {

		for (Map.Entry<Class<?>, Set<Annotation>> entry : getRepositoryTypes()) {

			Class<?> repositoryType = entry.getKey();
			Set<Annotation> qualifiers = entry.getValue();

			CdiRepositoryBean<?> repositoryBean = createRepositoryBean(repositoryType, qualifiers, beanManager);
			afterBeanDiscovery.addBean(repositoryBean);
			registerBean(repositoryBean);
		}
	}

	private <T> CdiRepositoryBean<T> createRepositoryBean(Class<T> repositoryType, Set<Annotation> qualifiers,
			BeanManager beanManager) {

		Bean<DynamoDbOperations> dynamoDbOperationsBean = Optional
				.ofNullable(this.dynamoDbOperationsMap.get(qualifiers))
				.orElseThrow(() -> new UnsatisfiedResolutionException(
						String.format("Unable to resolve a bean for '%s' with qualifiers %s.",
								DynamoDbOperations.class.getName(), qualifiers)));

		return new DynamoDbRepositoryBean<>(dynamoDbOperationsBean, qualifiers, repositoryType, beanManager,
				getCustomImplementationDetector());
	}
}
