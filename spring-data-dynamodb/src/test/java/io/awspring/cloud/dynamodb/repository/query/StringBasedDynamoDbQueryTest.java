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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.Query;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ValueExpressionDelegate;

public class StringBasedDynamoDbQueryTest {

	@Table(tableName = "widgets")
	static class Widget {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String round;
	}

	interface WidgetRepository extends Repository<Widget, String> {

		@Query(keyConditionExpression = "pk = :pk", indexName = "gsi1")
		List<Widget> rawByPk(@Param("pk") String pk);

		@Query(keyConditionExpression = "pk = :0", indexName = "gsi1")
		List<Widget> rawByPositional(String pk);

		@Query(filterExpression = "#s = :round", allowScan = true)
		List<Widget> scanByStatus(@Param("round") String round);
	}

	private StringBasedDynamoDbQuery queryFor(PartTreeDynamoDbQueryReplayTest.CapturingOperations operations,
			String name, Class<?>... paramTypes) throws NoSuchMethodException {
		Method method = WidgetRepository.class.getMethod(name, paramTypes);
		RepositoryMetadata metadata = new DefaultRepositoryMetadata(WidgetRepository.class);
		ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Widget.class);
		DynamoDbQueryMethod queryMethod = new DynamoDbQueryMethod(method, metadata, projectionFactory, mappingContext);
		return new StringBasedDynamoDbQuery(queryMethod, operations, ValueExpressionDelegate.create());
	}

	private PartTreeDynamoDbQueryReplayTest.CapturingOperations operations() {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		mappingContext.getRequiredPersistentEntity(Widget.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(mappingContext);
		converter.afterPropertiesSet();
		return new PartTreeDynamoDbQueryReplayTest.CapturingOperations(converter);
	}

	@Test
	void keyConditionExpressionWithIndexNameProducesARawResolvedQueryRequest() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "rawByPk", String.class);

		query.execute(new Object[] { "cust-1" });

		assertNotNull(operations.lastCapturedRequest);
		assertEquals("gsi1", operations.lastCapturedRequest.getIndexName());
		assertEquals("pk = :pk", operations.lastCapturedRequest.getKeyConditionExpression());
		assertEquals("cust-1", operations.lastCapturedRequest.getExpressionAttributeValues().get(":pk"));
		assertNull(operations.lastCapturedScanRequest);
	}

	@Test
	void positionalPlaceholderBindsByParameterIndex() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "rawByPositional", String.class);

		query.execute(new Object[] { "cust-9" });

		assertNotNull(operations.lastCapturedRequest);
		assertEquals("pk = :0", operations.lastCapturedRequest.getKeyConditionExpression());
		assertEquals("cust-9", operations.lastCapturedRequest.getExpressionAttributeValues().get(":0"));
	}

	@Test
	void filterExpressionOnlyProducesAScanWithAResolvableFilter() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "scanByStatus", String.class);

		query.execute(new Object[] { "ACTIVE" });

		assertNotNull(operations.lastCapturedScanRequest);
		assertEquals("#s = :round", operations.lastCapturedScanRequest.getFilterExpression());
		assertEquals("ACTIVE", operations.lastCapturedScanRequest.getExpressionAttributeValues().get(":round"));
		assertNull(operations.lastCapturedRequest);
	}

	@Test
	void namedBindingLeavesTheTokenVerbatimInTheExpression() throws NoSuchMethodException {
		PartTreeDynamoDbQueryReplayTest.CapturingOperations operations = operations();
		StringBasedDynamoDbQuery query = queryFor(operations, "rawByPk", String.class);

		query.execute(new Object[] { "cust-2" });

		assertNotNull(operations.lastCapturedRequest);
		assertFalse(operations.lastCapturedRequest.getKeyConditionExpression().contains("__spel_"));
		assertEquals("cust-2", operations.lastCapturedRequest.getExpressionAttributeValues().get(":pk"));
	}
}
