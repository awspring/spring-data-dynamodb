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
import io.awspring.cloud.dynamodb.core.EntityQueryResult;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.repository.support.DynamoDbEntityInformation;
import io.awspring.cloud.dynamodb.request.DynamoDbPageRequest;
import io.awspring.cloud.dynamodb.request.DynamoDbQueryRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class SimpleAggregateRepository<A> implements AggregateRepository<A> {

	private final DynamoDbOperations operations;

	private final Class<A> aggregateType;

	private final String partitionKeyColumn;

	@Nullable
	private final String sortKeyColumn;

	public SimpleAggregateRepository(DynamoDbEntityInformation<A, ?> entityInformation, DynamoDbOperations operations) {
		Assert.notNull(entityInformation, "entityInformation must not be null");
		Assert.notNull(operations, "operations must not be null");

		this.operations = operations;
		this.aggregateType = entityInformation.getJavaType();

		DynamoDbPersistentEntity<?> entity = operations.getConverter().getMappingContext()
				.getRequiredPersistentEntity(this.aggregateType);
		Assert.state(entity.isAggregateView(), () -> this.aggregateType.getName() + " is not an @AggregateTable class");

		this.partitionKeyColumn = entity.getAggregatePartitionKeyColumn();
		this.sortKeyColumn = entity.getAggregateSortKeyColumn();
	}

	@Override
	public Optional<A> findByPartitionKey(Object partitionKey) {
		Assert.notNull(partitionKey, "partitionKey must not be null");
		return queryPartition(buildRequest(partitionKey, null, Map.of()));
	}

	@Override
	public Optional<A> findByPartitionKeyAndSortKey(Object partitionKey, Object sortKey) {
		Assert.notNull(partitionKey, "partitionKey must not be null");
		Assert.notNull(sortKey, "sortKey must not be null");
		return queryPartition(buildRequest(partitionKey, "#sk = :sk", Map.of(":sk", sortKey)));
	}

	@Override
	public Optional<A> findByPartitionKeyAndSortKeyBetween(Object partitionKey, Object lo, Object hi) {
		Assert.notNull(partitionKey, "partitionKey must not be null");
		Assert.notNull(lo, "lo must not be null");
		Assert.notNull(hi, "hi must not be null");
		return queryPartition(buildRequest(partitionKey, "#sk BETWEEN :lo AND :hi", Map.of(":lo", lo, ":hi", hi)));
	}

	@Override
	public Optional<A> findByPartitionKeyAndSortKeyStartingWith(Object partitionKey, String prefix) {
		Assert.notNull(partitionKey, "partitionKey must not be null");
		Assert.notNull(prefix, "prefix must not be null");
		return queryPartition(buildRequest(partitionKey, "begins_with(#sk, :p)", Map.of(":p", prefix)));
	}

	@Override
	public boolean existsByPartitionKey(Object partitionKey) {
		Assert.notNull(partitionKey, "partitionKey must not be null");
		return findByPartitionKey(partitionKey).isPresent();
	}

	private DynamoDbQueryRequest buildRequest(Object partitionKey, @Nullable String sortCondition,
			Map<String, Object> sortValues) {
		Map<String, String> names = new LinkedHashMap<>();
		names.put("#pk", this.partitionKeyColumn);

		Map<String, Object> values = new LinkedHashMap<>();
		values.put(":pk", partitionKey);

		String keyConditionExpression = "#pk = :pk";
		if (sortCondition != null) {
			Assert.state(StringUtils.hasText(this.sortKeyColumn),
					() -> this.aggregateType.getName() + " has no @AggregateTable.sortKey() to bind a sort-key "
							+ "condition to; a sort-key query method requires a resolvable sort-key column");
			names.put("#sk", this.sortKeyColumn);
			keyConditionExpression = keyConditionExpression + " AND " + sortCondition;
			values.putAll(sortValues);
		}

		return DynamoDbQueryRequest.request().withKeyConditionExpression(keyConditionExpression)
				.withExpressionAttributeNames(names).withExpressionAttributeValues(values).build();
	}

	private Optional<A> queryPartition(DynamoDbQueryRequest request) {
		EntityQueryResult<A> result = operations.queryAggregate(aggregateType, request, DynamoDbPageRequest.of(null));
		if (result == null) {
			return Optional.empty();
		}
		Integer count = result.getCount();
		if (count == null || count == 0) {
			return Optional.empty();
		}
		return Optional.ofNullable(result.getEntity());
	}
}
