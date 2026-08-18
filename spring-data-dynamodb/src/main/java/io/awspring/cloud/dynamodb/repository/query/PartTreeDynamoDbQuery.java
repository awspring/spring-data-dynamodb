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

import io.awspring.cloud.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentProperty;
import java.util.Collections;
import java.util.Iterator;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class PartTreeDynamoDbQuery extends AbstractDynamoDbQuery {

	private final MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext;
	private final PartTree tree;
	private final Class<?> domainClass;

	public PartTreeDynamoDbQuery(DynamoDbQueryMethod queryMethod, DynamoDbOperations operations) {
		super(queryMethod, operations);

		this.domainClass = queryMethod.getResultProcessor().getReturnedType().getDomainType();
		this.tree = new PartTree(queryMethod.getName(), this.domainClass);
		this.mappingContext = operations.getConverter().getMappingContext();

		verifyServableEagerly(queryMethod);
	}

	private void verifyServableEagerly(DynamoDbQueryMethod queryMethod) {

		int parameterCount = (int) queryMethod.getParameters().getBindableParameters().stream().count();
		DynamoDbQuerySpec eagerSpec = new DynamoDbQueryCreator(this.tree,
				new PlaceholderParameterAccessor(parameterCount), this.mappingContext, this.domainClass).createQuery();

		if (eagerSpec.requiresScan() && !queryMethod.allowsScan()) {
			throw new InvalidDataAccessApiUsageException("Query method " + queryMethod
					+ " requires a full-table Scan (no index can serve '" + this.tree
					+ "' as a Query) but is not annotated @AllowScan. "
					+ "Add @AllowScan to accept the Scan, or add/adjust a GSI so an index can serve this query.");
		}
	}

	protected PartTree getTree() {
		return this.tree;
	}

	@Override
	protected Class<?> getDomainClass() {
		return this.domainClass;
	}

	@Override
	protected DynamoDbQuerySpec createQuerySpec(ParameterAccessor accessor) {
		return new DynamoDbQueryCreator(getTree(), accessor, this.mappingContext, getDomainClass()).createQuery();
	}

	@Override
	protected boolean isLimiting() {
		return this.tree.isLimiting();
	}

	@Override
	@Nullable
	protected Integer derivedResultLimit() {
		return this.tree.isLimiting() ? this.tree.getMaxResults() : null;
	}

	private static final class PlaceholderParameterAccessor implements ParameterAccessor {

		private final int parameterCount;

		PlaceholderParameterAccessor(int parameterCount) {
			this.parameterCount = parameterCount;
		}

		@Override
		public ScrollPosition getScrollPosition() {
			throw new UnsupportedOperationException("PlaceholderParameterAccessor is shape-only (D1 eager check)");
		}

		@Override
		public Pageable getPageable() {
			throw new UnsupportedOperationException("PlaceholderParameterAccessor is shape-only (D1 eager check)");
		}

		@Override
		public Sort getSort() {
			return Sort.unsorted();
		}

		@Override
		public Class<?> findDynamicProjection() {
			return null;
		}

		@Override
		public Object getBindableValue(int index) {
			return null;
		}

		@Override
		public boolean hasBindableNullValue() {
			return false;
		}

		@Override
		public Iterator<Object> iterator() {
			return Collections.nCopies(parameterCount, (Object) null).iterator();
		}
	}
}
