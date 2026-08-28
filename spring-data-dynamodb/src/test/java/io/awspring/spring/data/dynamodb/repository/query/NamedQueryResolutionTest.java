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
package io.awspring.spring.data.dynamodb.repository.query;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.awspring.spring.data.dynamodb.core.DynamoDbOperations;
import io.awspring.spring.data.dynamodb.core.converter.DynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.repository.AllowScan;
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepositoryFactory;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.core.support.PropertiesBasedNamedQueries;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ValueExpressionDelegate;

@DisplayName("Named query resolution")
class NamedQueryResolutionTest {

	private static final String TABLE_NAME = "test_entity";
	private static final String NAMED_QUERY_NAME = "TestEntity.findByNamedQuery";
	private static final String NAMED_QUERY_EXPRESSION = "#s = :round";

	@Table(tableName = TABLE_NAME)
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

	interface TestRepository extends Repository<TestEntity, String> {
		@AllowScan
		List<TestEntity> findByNamedQuery(String round);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static <M extends DynamoDbPersistentEntity<?>> void stubMappingContext(DynamoDbConverter converter,
                                                                                   MappingContext<M, DynamoDbPersistentProperty> mappingContext) {
		when(converter.getMappingContext()).thenReturn((MappingContext) mappingContext);
	}

	private static final class TestDynamoDbRepositoryFactory extends DynamoDbRepositoryFactory {

		private TestDynamoDbRepositoryFactory(DynamoDbOperations operations) {
			super(operations);
		}

		private QueryLookupStrategy queryLookupStrategy(Key key) {
			return getQueryLookupStrategy(key, ValueExpressionDelegate.create()).orElseThrow();
		}
	}

	private RepositoryQuery resolveQuery(NamedQueries namedQueries) throws NoSuchMethodException {
		return resolveQuery(namedQueries, Key.CREATE_IF_NOT_FOUND);
	}

	private RepositoryQuery resolveQuery(NamedQueries namedQueries, Key key) throws NoSuchMethodException {
		RepositoryMetadata metadata = new DefaultRepositoryMetadata(TestRepository.class);
		Method method = TestRepository.class.getMethod("findByNamedQuery", String.class);
		ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();

		DynamoDbOperations operations = mock(DynamoDbOperations.class);
		DynamoDbConverter converter = mock(DynamoDbConverter.class);
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		when(operations.getConverter()).thenReturn(converter);
		stubMappingContext(converter, mappingContext);

		return new TestDynamoDbRepositoryFactory(operations).queryLookupStrategy(key).resolveQuery(method, metadata,
				projectionFactory, namedQueries);
	}

	@Nested
	@DisplayName("Named query present")
	class NamedQueryPresentTests {

		@Test
		@DisplayName("named query takes precedence over derived query")
		void namedQueryTakesPrecedenceOverDerivedQuery() throws NoSuchMethodException {
			// Arrange
			Properties namedQueryProps = new Properties();
			namedQueryProps.setProperty(NAMED_QUERY_NAME, NAMED_QUERY_EXPRESSION);
			NamedQueries namedQueries = new PropertiesBasedNamedQueries(namedQueryProps);

			// Act
			RepositoryQuery resolvedQuery = resolveQuery(namedQueries);

			// Assert
			Assertions.assertAll(
					() -> Assertions.assertNotNull(resolvedQuery,
							"resolveQuery must return a RepositoryQuery instance"),
					() -> Assertions.assertInstanceOf(StringBasedDynamoDbQuery.class, resolvedQuery,
							"Named query should resolve to StringBasedDynamoDbQuery, not PartTreeDynamoDbQuery"),
					() -> Assertions.assertEquals(
							new DynamoDbQueryMethod(TestRepository.class.getMethod("findByNamedQuery", String.class),
									new DefaultRepositoryMetadata(TestRepository.class),
									new SpelAwareProxyProjectionFactory(), new DynamoDbMappingContext()).getName(),
							resolvedQuery.getQueryMethod().getName(),
							"Resolved query must wrap the correct DynamoDbQueryMethod"));
		}
	}

	@Nested
	@DisplayName("Query lookup strategy")
	class QueryLookupStrategyTests {

		@Test
		@DisplayName("USE_DECLARED_QUERY rejects a method without a named or annotated query")
		void useDeclaredQueryWithoutDeclarationFailsFast() {
			NamedQueries namedQueries = new PropertiesBasedNamedQueries(new Properties());

			Assertions.assertThrows(InvalidDataAccessApiUsageException.class,
					() -> resolveQuery(namedQueries, Key.USE_DECLARED_QUERY));
		}

		@Test
		@DisplayName("CREATE ignores a matching named query and derives from the method name")
		void createIgnoresNamedQuery() throws NoSuchMethodException {
			Properties properties = new Properties();
			properties.setProperty(NAMED_QUERY_NAME, NAMED_QUERY_EXPRESSION);

			RepositoryQuery resolved = resolveQuery(new PropertiesBasedNamedQueries(properties), Key.CREATE);

			Assertions.assertInstanceOf(PartTreeDynamoDbQuery.class, resolved);
		}
	}

	@Nested
	@DisplayName("Named query naming convention")
	class NamingConventionTests {

		@Test
		@DisplayName("named query name follows EntityName.methodName convention")
		void namedQueryNameConventionMatchesRepositoryMethod() throws NoSuchMethodException {
			// Arrange
			RepositoryMetadata metadata = new DefaultRepositoryMetadata(TestRepository.class);
			Method method = TestRepository.class.getMethod("findByNamedQuery", String.class);
			ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
			DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();

			// Act
			DynamoDbQueryMethod queryMethod = new DynamoDbQueryMethod(method, metadata, projectionFactory,
					mappingContext);
			String namedQueryName = queryMethod.getNamedQueryName();

			// Assert
			Assertions.assertEquals(NAMED_QUERY_NAME, namedQueryName,
					"Named query name must follow EntityName.methodName convention");
		}
	}

	@Nested
	@DisplayName("Named query absent (fallback)")
	class FallbackTests {

		@Test
		@DisplayName("falls through to derived query when no named query exists")
		void whenNoNamedQueryExistsFallsThroughToDerivedQuery() throws NoSuchMethodException {
			// Arrange
			NamedQueries namedQueries = new PropertiesBasedNamedQueries(new Properties());

			// Act
			RepositoryQuery resolvedQuery = resolveQuery(namedQueries);

			// Assert
			Assertions.assertAll(
					() -> Assertions.assertNotNull(resolvedQuery,
							"resolveQuery must return a RepositoryQuery instance"),
					() -> Assertions.assertInstanceOf(PartTreeDynamoDbQuery.class, resolvedQuery,
							"Without a named query or @Query annotation, method should resolve to PartTreeDynamoDbQuery"));
		}
	}
}
