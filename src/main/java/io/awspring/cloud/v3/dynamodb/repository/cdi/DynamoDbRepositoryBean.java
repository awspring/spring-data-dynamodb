package io.awspring.cloud.v3.dynamodb.repository.cdi;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import io.awspring.cloud.v3.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.v3.dynamodb.repository.DynamoDbRepositoryFactory;
import org.springframework.data.repository.cdi.CdiRepositoryBean;
import org.springframework.data.repository.config.CustomRepositoryImplementationDetector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;

public class DynamoDbRepositoryBean<T> extends CdiRepositoryBean<T> {

	private final Bean<DynamoDbOperations> dynamoDbOperationsBean;

	public DynamoDbRepositoryBean(Bean<DynamoDbOperations> operations, Set<Annotation> qualifiers,
								   Class<T> repositoryType, BeanManager beanManager, @Nullable CustomRepositoryImplementationDetector detector) {
		super(qualifiers, repositoryType, beanManager, Optional.ofNullable(detector));

		Assert.notNull(operations, "Cannot create repository with 'null' for DynamoDbOperations.");
		this.dynamoDbOperationsBean = operations;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.cdi.CdiRepositoryBean#create(javax.enterprise.context.spi.CreationalContext, java.lang.Class)
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
