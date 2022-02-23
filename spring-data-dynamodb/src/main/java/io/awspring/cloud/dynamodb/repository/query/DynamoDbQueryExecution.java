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

import io.awspring.cloud.dynamodb.core.EntityQueryResult;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;

public interface DynamoDbQueryExecution {

	@Nullable
	Object execute(EntityQueryResult<List<Object>> result);

	record CollectionExecution() implements DynamoDbQueryExecution {

	@Override
		public Object execute(EntityQueryResult<List<Object>> result) {
			return result.getEntity();
		}}

	record SingleEntityExecution(boolean limiting) implements DynamoDbQueryExecution {

	@Override
		@Nullable
		public Object execute(EntityQueryResult<List<Object>> result) {
			List<Object> items = result.getEntity();
			if (items.isEmpty()) {
				return null;
			}
			if (!limiting && items.size() > 1) {
				throw new IncorrectResultSizeDataAccessException(1, items.size());
			}
			return items.get(0);
		}}

	record WindowExecution() implements DynamoDbQueryExecution {

	@Override
		public Object execute(EntityQueryResult<List<Object>> result) {
			Map<String, Object> lastEvaluatedKey = result.getLastEvaluatedKey();
			boolean hasNext = lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty();
			ScrollPosition nextPosition = hasNext
					? ScrollPosition.forward(lastEvaluatedKey)
					: ScrollPosition.keyset();
			return Window.from(result.getEntity(), index -> nextPosition, hasNext);
		}
}}
