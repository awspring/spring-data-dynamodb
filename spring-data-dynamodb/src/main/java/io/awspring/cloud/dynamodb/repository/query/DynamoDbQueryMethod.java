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
package io.awspring.cloud.dynamodb.repository.query;

import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.cloud.dynamodb.repository.AllowScan;
import io.awspring.cloud.dynamodb.repository.Modifying;
import io.awspring.cloud.dynamodb.repository.Query;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.DefaultParameters;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.ParametersSource;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.util.StringUtils;

public class DynamoDbQueryMethod extends QueryMethod {

	private final Method method;
	private final MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext;
	private final @Nullable Query query;
	private final boolean allowScan;

	public DynamoDbQueryMethod(Method method, RepositoryMetadata metadata, ProjectionFactory projectionFactory,
			MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext) {

		super(method, metadata, projectionFactory, DefaultParameters::new);

		this.method = method;
		this.mappingContext = mappingContext;
		this.query = AnnotatedElementUtils.findMergedAnnotation(method, Query.class);
		this.allowScan = AnnotatedElementUtils.hasAnnotation(method, AllowScan.class)
				|| (this.query != null && this.query.allowScan());

		verify(method, metadata);
	}

	@SuppressWarnings("unused")
	public void verify(Method method, RepositoryMetadata metadata) {

		if (Page.class.isAssignableFrom(method.getReturnType())) {
			throw new InvalidDataAccessApiUsageException(
					"Page queries are not supported on DynamoDB (a total count requires a second full Scan); "
							+ "use Window<T> (keyset-based, see ScrollPosition/KeysetScrollPosition) instead. "
							+ "Method: " + method);
		}

		if (org.springframework.data.domain.Slice.class.isAssignableFrom(method.getReturnType())) {
			throw new InvalidDataAccessApiUsageException(
					"Slice queries are not supported on DynamoDB: Slice is backed by an offset-based Pageable and "
							+ "DynamoDB paginates by keyset, so Slice.nextPageable() cannot advance. Use Window<T> "
							+ "(keyset-based, see ScrollPosition/KeysetScrollPosition) instead. Method: " + method);
		}

		if (query != null && StringUtils.hasText(query.keyConditionExpression())) {
			if (!StringUtils.hasText(query.indexName())) {
				throw new InvalidDataAccessApiUsageException(
						"@Query(keyConditionExpression=...) requires indexName() to be set explicitly -- "
								+ "there is no DynamoDbQuerySpec to auto-select an index from on this escape hatch. "
								+ "Method: " + method);
			}
			if (StringUtils.hasText(query.partiQl())) {
				throw new InvalidDataAccessApiUsageException(
						"@Query cannot combine keyConditionExpression() with partiQl() on the same method: " + method);
			}
		}

		if (query != null && StringUtils.hasText(query.updateExpression())
				&& !AnnotatedElementUtils.hasAnnotation(method, Modifying.class)) {
			throw new InvalidDataAccessApiUsageException(
					"@Query(updateExpression=...) requires @Modifying. Method: " + method);
		}

		if (!isCountQuery() && !isExistsQuery() && !isModifyingQuery()
				&& getResultProcessor().getReturnedType().isProjecting()) {
			throw new InvalidDataAccessApiUsageException(
					"DTO / interface projections are not supported in this alpha. Declare the query method to return "
							+ "its entity type (optionally wrapped in Optional, List, Window or Slice). Method: "
							+ method);
		}
	}

	@Override
	protected Parameters<?, ?> createParameters(ParametersSource parametersSource) {
		return new DefaultParameters(parametersSource);
	}

	public boolean hasAnnotatedQuery() {
		return this.query != null;
	}

	@Nullable
	public Query getQueryAnnotation() {
		return this.query;
	}

	public boolean allowsScan() {
		return this.allowScan;
	}

	public boolean isModifyingQuery() {
		return AnnotatedElementUtils.hasAnnotation(this.method, Modifying.class);
	}

	public boolean isCountQuery() {
		Class<?> returnType = this.method.getReturnType();
		return returnType == long.class || returnType == Long.class;
	}

	public boolean isExistsQuery() {
		Class<?> returnType = this.method.getReturnType();
		return returnType == boolean.class || returnType == Boolean.class;
	}

	public boolean isPartiQlQuery() {
		return this.query != null && StringUtils.hasText(this.query.partiQl());
	}

	public boolean wantsTypeFilter() {
		return this.query == null || this.query.typeFilter();
	}

	protected MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> getMappingContext() {
		return this.mappingContext;
	}
}
