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
package io.awspring.cloud.dynamodb.repository.support;

import io.awspring.cloud.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.dynamodb.repository.DynamoDbRepositoryFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.util.Assert;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbRepositoryFactoryBean<T extends Repository<S, ID>, S, ID>
		extends RepositoryFactoryBeanSupport<T, S, ID> {

	private boolean mappingContextConfigured = false;

	private @Nullable DynamoDbOperations dynamoDbOperations;

	public DynamoDbRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
		super(repositoryInterface);
	}

	@Override
	protected RepositoryFactorySupport createRepositoryFactory() {
		Assert.state(this.dynamoDbOperations != null, "DynamoDbOperations must not be null");
		return getFactoryInstance(this.dynamoDbOperations);
	}

	protected DynamoDbRepositoryFactory getFactoryInstance(DynamoDbOperations operations) {
		return new DynamoDbRepositoryFactory(operations);
	}

	public void setDynamoDbOperations(DynamoDbOperations dynamoDbOperations) {
		this.dynamoDbOperations = dynamoDbOperations;
	}

	@Override
	protected void setMappingContext(MappingContext<?, ?> mappingContext) {
		super.setMappingContext(mappingContext);
		this.mappingContextConfigured = true;
	}

	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();

		Assert.notNull(this.dynamoDbOperations, "DynamoDbOperations must not be null");

		if (!this.mappingContextConfigured) {
			setMappingContext(this.dynamoDbOperations.getConverter().getMappingContext());
		}
	}
}
