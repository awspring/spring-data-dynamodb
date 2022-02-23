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

import org.springframework.util.Assert;

public interface NamingStrategy {

	NamingStrategy INSTANCE = new NamingStrategy() {
	};

	default String getTableName(DynamoDbPersistentEntity<?> entity) {

		Assert.notNull(entity, "DynamoDbPersistentEntity must not be null");

		return entity.getType().getSimpleName();
	}

	default String getUserDefinedTypeName(DynamoDbPersistentEntity<?> entity) {

		Assert.notNull(entity, "DynamoDbPersistentEntity must not be null");

		return entity.getType().getSimpleName();
	}

	default String getColumnName(DynamoDbPersistentProperty property) {

		Assert.notNull(property, "DynamoDbPersistentProperty must not be null");

		return property.getName();
	}

}
