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
package io.awspring.cloud.dynamodb.core.mapping;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public record IndexKeySchema(List<DynamoDbPersistentProperty> partitionKeys,
							 List<DynamoDbPersistentProperty> sortKeys) {

	public IndexKeySchema(List<DynamoDbPersistentProperty> partitionKeys,
						  List<DynamoDbPersistentProperty> sortKeys) {
		this.partitionKeys = List.copyOf(partitionKeys);
		this.sortKeys = List.copyOf(sortKeys);
	}

	public boolean isEmpty() {
		return partitionKeys.isEmpty() && sortKeys.isEmpty();
	}

	@Nullable
	public DynamoDbPersistentProperty singlePartitionKey() {
		return partitionKeys.size() == 1 ? partitionKeys.get(0) : null;
	}

	@Nullable
	public DynamoDbPersistentProperty singleSortKey() {
		return sortKeys.size() == 1 ? sortKeys.get(0) : null;
	}
}
