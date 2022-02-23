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
package io.awspring.cloud.dynamodb.repository;

import io.awspring.cloud.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.cloud.dynamodb.repository.query.DynamoDbQueryMethod;
import io.awspring.cloud.dynamodb.repository.query.PartTreeDynamoDbQuery;
import io.awspring.cloud.dynamodb.repository.query.StringBasedDynamoDbQuery;
import io.awspring.cloud.dynamodb.repository.support.DynamoDbEntityInformation;
import io.awspring.cloud.dynamodb.repository.support.MappingDynamoDbEntityInformation;
import java.lang.reflect.Method;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.query.CachingValueExpressionDelegate;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.util.Assert;

public class DynamoDbRepositoryFactory extends RepositoryFactorySupport {

	private final MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext;

	private final DynamoDbOperations operations;

	public DynamoDbRepositoryFactory(DynamoDbOperations operations) {
		Assert.notNull(operations, "Operation cannot be null!");

		this.mappingContext = operations.getConverter().getMappingContext();
		this.operations = operations;
	}

	@Override
	public <T, ID> DynamoDbEntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
		return getEntityInformation(domainClass, false);
	}

	private <T, ID> DynamoDbEntityInformation<T, ID> getEntityInformation(Class<T> domainClass,
			boolean secondaryIndexView) {
		DynamoDbPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(domainClass);
		return new MappingDynamoDbEntityInformation<>((DynamoDbPersistentEntity<T>) entity, operations.getConverter(),
				secondaryIndexView);
	}

	@Override
	protected Object getTargetRepository(RepositoryInformation information) {
		DynamoDbEntityInformation<?, Object> entityInformation = getEntityInformation(information.getDomainType(),
				isSecondaryIndexView(information));
		return getTargetRepositoryViaReflection(information, entityInformation, operations);
	}

	private static boolean isSecondaryIndexView(RepositoryMetadata metadata) {
		return SecondaryIndexRepository.class.isAssignableFrom(metadata.getRepositoryInterface());
	}

	@Override
	protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
		return SimpleDynamoDbRepository.class;
	}

	@Override
	protected Optional<QueryLookupStrategy> getQueryLookupStrategy(@Nullable Key key,
			ValueExpressionDelegate valueExpressionDelegate) {
		return Optional.of(new DynamoDbQueryLookupStrategy(operations,
				new CachingValueExpressionDelegate(valueExpressionDelegate), mappingContext));
	}

	private record DynamoDbQueryLookupStrategy(DynamoDbOperations operations,
			ValueExpressionDelegate valueExpressionDelegate,
			MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext)
			implements QueryLookupStrategy {

	@Override
	public RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata, ProjectionFactory factory,
			NamedQueries namedQueries) {

		DynamoDbQueryMethod queryMethod = new DynamoDbQueryMethod(method, metadata, factory, mappingContext);
		String namedQueryName = queryMethod.getNamedQueryName();

		if (namedQueries.hasQuery(namedQueryName)) {
			String namedQueryString = namedQueries.getQuery(namedQueryName);
			return new StringBasedDynamoDbQuery(queryMethod, operations, valueExpressionDelegate, namedQueryString);
		}

		if (queryMethod.hasAnnotatedQuery()) {
			return new StringBasedDynamoDbQuery(queryMethod, operations, valueExpressionDelegate);
		}

		return new PartTreeDynamoDbQuery(queryMethod, operations);
	}
}}
