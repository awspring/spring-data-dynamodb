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
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepositoryFactory;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.data.repository.cdi.CdiRepositoryBean;
import org.springframework.data.repository.config.CustomRepositoryImplementationDetector;
import org.springframework.util.Assert;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbRepositoryBean<T> extends CdiRepositoryBean<T> {

	private final Bean<DynamoDbOperations> dynamoDbOperationsBean;

	public DynamoDbRepositoryBean(Bean<DynamoDbOperations> operations, Set<Annotation> qualifiers,
			Class<T> repositoryType, BeanManager beanManager,
			@Nullable CustomRepositoryImplementationDetector detector) {
		super(qualifiers, repositoryType, beanManager, Optional.ofNullable(detector));

		Assert.notNull(operations, "Cannot create repository with 'null' for DynamoDbOperations.");
		this.dynamoDbOperationsBean = operations;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.data.repository.cdi.CdiRepositoryBean#create(javax.enterprise.context.spi.CreationalContext,
	 * java.lang.Class)
	 */
	@Override
	protected T create(CreationalContext<T> creationalContext, Class<T> repositoryType) {

		DynamoDbOperations dynamoDbOperations = getDependencyInstance(dynamoDbOperationsBean, DynamoDbOperations.class);

		return create(() -> new DynamoDbRepositoryFactory(dynamoDbOperations), repositoryType);
	}

	@Override
	public Class<? extends Annotation> getScope() {
		return dynamoDbOperationsBean.getScope();
	}
}
