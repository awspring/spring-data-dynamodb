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
import io.awspring.cloud.dynamodb.repository.AggregateRepository;
import io.awspring.cloud.dynamodb.repository.AllowScan;
import io.awspring.cloud.dynamodb.repository.Modifying;
import io.awspring.cloud.dynamodb.repository.Query;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.DefaultParameters;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.ParametersSource;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.util.StringUtils;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbQueryMethod extends QueryMethod {

	private final Method method;
	private final MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext;
	private final @Nullable Query query;
	private final boolean allowScan;

	private @Nullable PartTree partTree;
	private boolean partTreeUnparseable;

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

		if (Slice.class.isAssignableFrom(method.getReturnType())) {
			throw new InvalidDataAccessApiUsageException(
					"Slice queries are not supported on DynamoDB: Slice is backed by an offset-based Pageable and "
							+ "DynamoDB paginates by keyset, so Slice.nextPageable() cannot advance. Use Window<T> "
							+ "(keyset-based, see ScrollPosition/KeysetScrollPosition) instead. Method: " + method);
		}

		if (AggregateRepository.class.isAssignableFrom(metadata.getRepositoryInterface())
				&& AnnotatedElementUtils.hasAnnotation(method, Modifying.class)) {
			throw new InvalidDataAccessApiUsageException(
					"@Modifying is not supported on an AggregateRepository: an @AggregateTable is a read-only "
							+ "projection and never writes the underlying @Table entities. Remove @Modifying, or move "
							+ "the write to a DynamoDbRepository / DynamoDbOperations. Method: " + method);
		}

		if (query != null && StringUtils.hasText(query.keyConditionExpression())) {
			boolean isAggregateRepo = AggregateRepository.class.isAssignableFrom(metadata.getRepositoryInterface());
			if (!isAggregateRepo && !StringUtils.hasText(query.indexName())) {
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

		if (query != null && query.limit() == 0) {
			throw new InvalidDataAccessApiUsageException(
					"@Query(limit = 0) is not a valid DynamoDB Limit -- Limit must be at least 1. Leave limit() unset "
							+ "(the default of -1) to page without an explicit limit. Method: " + method);
		}

		if (isModifyingQuery()) {
			verifyModifyingReturnType(method);
		}

		PartTree tree = partTree();
		if (tree != null && tree.isLimiting() && getParameters().hasLimitParameter()) {
			throw new InvalidDataAccessApiUsageException("Query method combines a derived limit (Top/First, capping at "
					+ tree.getMaxResults() + ") with a Limit parameter; the two would silently disagree. Keep the "
					+ "Top/First keyword or the Limit parameter, not both. Method: " + method);
		}

		if (!isCountQuery() && !isExistsQuery() && !isModifyingQuery()
				&& getResultProcessor().getReturnedType().isProjecting()) {
			throw new InvalidDataAccessApiUsageException(
					"DTO / interface projections are not supported in this alpha. Declare the query method to return "
							+ "its entity type (optionally wrapped in Optional, List, Window or Slice). Method: "
							+ method);
		}
	}

	private void verifyModifyingReturnType(Method method) {

		Class<?> returnType = method.getReturnType();
		if (void.class.equals(returnType) || Void.class.equals(returnType) || boolean.class.equals(returnType)
				|| Boolean.class.equals(returnType) || int.class.equals(returnType) || Integer.class.equals(returnType)
				|| long.class.equals(returnType) || Long.class.equals(returnType)) {
			return;
		}

		Class<?> domainType = getDomainClass();
		if (returnType.isAssignableFrom(domainType) || domainType.isAssignableFrom(returnType)) {
			return;
		}

		throw new InvalidDataAccessApiUsageException("@Modifying query method returns " + returnType.getName()
				+ ", which a single UpdateItem cannot produce. Declare void, boolean, int/long (affected items) or "
				+ domainType.getSimpleName() + " (the updated entity). Method: " + method);
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
		Boolean fromTree = countFromPartTree();
		if (fromTree != null) {
			return fromTree;
		}
		Class<?> returnType = this.method.getReturnType();
		return returnType == long.class || returnType == Long.class;
	}

	public boolean isExistsQuery() {
		Boolean fromTree = existsFromPartTree();
		if (fromTree != null) {
			return fromTree;
		}
		Class<?> returnType = this.method.getReturnType();
		return returnType == boolean.class || returnType == Boolean.class;
	}

	@Nullable
	private Boolean countFromPartTree() {
		PartTree tree = partTree();
		return tree == null ? null : tree.isCountProjection();
	}

	@Nullable
	private Boolean existsFromPartTree() {
		PartTree tree = partTree();
		return tree == null ? null : tree.isExistsProjection();
	}

	@Nullable
	private PartTree partTree() {

		if (this.query != null) {
			return null;
		}
		if (this.partTree == null) {
			try {
				this.partTree = new PartTree(this.method.getName(), getDomainClass());
			}
			catch (RuntimeException ex) {
				this.partTreeUnparseable = true;
				return null;
			}
		}
		return this.partTreeUnparseable ? null : this.partTree;
	}

	public boolean isPartiQlQuery() {
		return this.query != null && StringUtils.hasText(this.query.partiQl());
	}

	public Class<?> getDeclaredReturnType() {
		return this.method.getReturnType();
	}

	public boolean wantsTypeFilter() {
		return this.query == null || this.query.typeFilter();
	}

	protected MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> getMappingContext() {
		return this.mappingContext;
	}
}
