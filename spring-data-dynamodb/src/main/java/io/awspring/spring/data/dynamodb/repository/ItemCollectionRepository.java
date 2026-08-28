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
package io.awspring.spring.data.dynamodb.repository;

import java.util.Optional;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Read-only repository that folds one DynamoDB item collection into a view.
 *
 * @author Matej Nedic
 * @since 1.0.0
 */
@NoRepositoryBean
public interface ItemCollectionRepository<A> extends Repository<A, Void> {

	/** Finds and folds every item with the partition key. */
	Optional<A> findByPartitionKey(Object partitionKey);

	/** Finds and folds the item at the complete key. */
	Optional<A> findByPartitionKeyAndSortKey(Object partitionKey, Object sortKey);

	/** Finds and folds items in the inclusive sort-key range. */
	Optional<A> findByPartitionKeyAndSortKeyBetween(Object partitionKey, Object lo, Object hi);

	/** Finds and folds items whose sort key starts with the prefix. */
	Optional<A> findByPartitionKeyAndSortKeyStartingWith(Object partitionKey, String prefix);

	/** Checks whether the partition contains at least one matching item. */
	boolean existsByPartitionKey(Object partitionKey);
}
