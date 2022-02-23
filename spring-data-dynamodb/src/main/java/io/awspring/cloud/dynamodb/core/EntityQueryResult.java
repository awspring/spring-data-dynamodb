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
package io.awspring.cloud.dynamodb.core;

import java.util.Map;

public class EntityQueryResult<T> {

	private final T entity;
	private final Integer count;

	private final Map<String, Object> lastEvaluatedKey;

	private EntityQueryResult(T entity, Integer count) {
		this(entity, count, null);
	}

	private EntityQueryResult(T entity, Integer count, Map<String, Object> lastEvaluatedKey) {
		this.entity = entity;
		this.count = count;
		this.lastEvaluatedKey = lastEvaluatedKey;
	}

	static <T> EntityQueryResult<T> of(T entity) {
		return new EntityQueryResult<T>(entity, null);
	}

	static <T> EntityQueryResult<T> of(T entity, Integer count) {
		return new EntityQueryResult<T>(entity, count);
	}

	static <T> EntityQueryResult<T> of(T entity, Integer count, Map<String, Object> lastEvaluatedKey) {
		return new EntityQueryResult<T>(entity, count, lastEvaluatedKey);
	}

	public T getEntity() {
		return entity;
	}

	public Integer getCount() {
		return count;
	}

	public Map<String, Object> getLastEvaluatedKey() {
		return lastEvaluatedKey;
	}
}
