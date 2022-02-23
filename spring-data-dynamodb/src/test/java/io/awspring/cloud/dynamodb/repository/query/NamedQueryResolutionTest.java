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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.awspring.cloud.dynamodb.core.DynamoDbOperations;
import io.awspring.cloud.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.AllowScan;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.core.support.PropertiesBasedNamedQueries;
import org.springframework.data.repository.query.CachingValueExpressionDelegate;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ValueExpressionDelegate;

public class NamedQueryResolutionTest {

	@Table(tableName = "test_entity")
	static class TestEntity {
		@PartitionKey
		private String id;
		private String round;
		private String namedQuery;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getStatus() {
			return round;
		}

		public void setStatus(String round) {
			this.round = round;
		}

		public String getNamedQuery() {
			return namedQuery;
		}

		public void setNamedQuery(String namedQuery) {
			this.namedQuery = namedQuery;
		}
	}

	interface TestRepository extends org.springframework.data.repository.Repository<TestEntity, String> {
		@AllowScan
		List<TestEntity> findByNamedQuery(String round);
	}

	private static <M extends DynamoDbPersistentEntity<?>> void stubMappingContext(DynamoDbConverter converter,
                                                                                   MappingContext<M, DynamoDbPersistentProperty> mappingContext) {
		when(converter.getMappingContext()).thenReturn((MappingContext) mappingContext);
	}

	@Test
	void namedQueryTakesPrecedenceOverDerivedQuery() throws NoSuchMethodException {
		Properties namedQueryProps = new Properties();
		namedQueryProps.setProperty("TestEntity.findByNamedQuery", "#s = :round");
		NamedQueries namedQueries = new PropertiesBasedNamedQueries(namedQueryProps);

		RepositoryMetadata metadata = new DefaultRepositoryMetadata(TestRepository.class);
		Method method = TestRepository.class.getMethod("findByNamedQuery", String.class);
		ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();

		DynamoDbOperations operations = mock(DynamoDbOperations.class);
		DynamoDbConverter converter = mock(DynamoDbConverter.class);
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		when(operations.getConverter()).thenReturn(converter);
		stubMappingContext(converter, mappingContext);

		ValueExpressionDelegate valueExpressionDelegate = new CachingValueExpressionDelegate(
				ValueExpressionDelegate.create());

		DynamoDbQueryMethod queryMethod = new DynamoDbQueryMethod(method, metadata, projectionFactory, mappingContext);
		String namedQueryName = queryMethod.getNamedQueryName();

		RepositoryQuery resolvedQuery = null;
		if (namedQueries.hasQuery(namedQueryName)) {
			String namedQueryString = namedQueries.getQuery(namedQueryName);
			resolvedQuery = new StringBasedDynamoDbQuery(queryMethod, operations, valueExpressionDelegate,
					namedQueryString);
		}
		else if (queryMethod.hasAnnotatedQuery()) {
			resolvedQuery = new StringBasedDynamoDbQuery(queryMethod, operations, valueExpressionDelegate);
		}
		else {
			resolvedQuery = new PartTreeDynamoDbQuery(queryMethod, operations);
		}

		Assertions.assertNotNull(resolvedQuery, "resolveQuery must return a RepositoryQuery instance");
		Assertions.assertInstanceOf(StringBasedDynamoDbQuery.class, resolvedQuery,
				"Named query should resolve to StringBasedDynamoDbQuery, not PartTreeDynamoDbQuery");
		Assertions.assertEquals(queryMethod, resolvedQuery.getQueryMethod(),
				"Resolved query must wrap the correct DynamoDbQueryMethod");
	}

	@Test
	void namedQueryNameConventionMatchesRepositoryMethod() throws NoSuchMethodException {

		RepositoryMetadata metadata = new DefaultRepositoryMetadata(TestRepository.class);
		Method method = TestRepository.class.getMethod("findByNamedQuery", String.class);
		ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
		MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext = new DynamoDbMappingContext();

		DynamoDbQueryMethod queryMethod = new DynamoDbQueryMethod(method, metadata, projectionFactory, mappingContext);
		String namedQueryName = queryMethod.getNamedQueryName();

		Assertions.assertEquals("TestEntity.findByNamedQuery", namedQueryName,
				"Named query name must follow EntityName.methodName convention");
	}

	@Test
	void whenNoNamedQueryExistsFallsThroughToDerivedQuery() throws NoSuchMethodException {
		NamedQueries namedQueries = new PropertiesBasedNamedQueries(new Properties());

		RepositoryMetadata metadata = new DefaultRepositoryMetadata(TestRepository.class);
		Method method = TestRepository.class.getMethod("findByNamedQuery", String.class);
		ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();

		DynamoDbOperations operations = mock(DynamoDbOperations.class);
		DynamoDbConverter converter = mock(DynamoDbConverter.class);
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		when(operations.getConverter()).thenReturn(converter);
		stubMappingContext(converter, mappingContext);

		ValueExpressionDelegate valueExpressionDelegate = new CachingValueExpressionDelegate(
				ValueExpressionDelegate.create());

		DynamoDbQueryMethod queryMethod = new DynamoDbQueryMethod(method, metadata, projectionFactory, mappingContext);
		String namedQueryName = queryMethod.getNamedQueryName();

		RepositoryQuery resolvedQuery = null;
		if (namedQueries.hasQuery(namedQueryName)) {
			String namedQueryString = namedQueries.getQuery(namedQueryName);
			resolvedQuery = new StringBasedDynamoDbQuery(queryMethod, operations, valueExpressionDelegate,
					namedQueryString);
		}
		else if (queryMethod.hasAnnotatedQuery()) {
			resolvedQuery = new StringBasedDynamoDbQuery(queryMethod, operations, valueExpressionDelegate);
		}
		else {
			resolvedQuery = new PartTreeDynamoDbQuery(queryMethod, operations);
		}

		Assertions.assertNotNull(resolvedQuery, "resolveQuery must return a RepositoryQuery instance");
		Assertions.assertInstanceOf(PartTreeDynamoDbQuery.class, resolvedQuery,
				"Without a named query or @Query annotation, method should resolve to PartTreeDynamoDbQuery");
	}
}
