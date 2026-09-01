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
package io.awspring.spring.data.dynamodb.core.mapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
class IndexKeySchemaBuilder {

	private record Entry(int order, DynamoDbPersistentProperty property) {
	}

	private final List<Entry> partitions = new ArrayList<>();
	private final List<Entry> sorts = new ArrayList<>();

	void add(KeyRole role, DynamoDbPersistentProperty property) {
		Entry entry = new Entry(role.order(), property);
		if (role.keyType() == KeyRole.KeyType.PARTITION) {
			partitions.add(entry);
		}
		else {
			sorts.add(entry);
		}
	}

	IndexKeySchema build() {
		return new IndexKeySchema(ordered(partitions), ordered(sorts));
	}

	private static List<DynamoDbPersistentProperty> ordered(List<Entry> entries) {
		return entries.stream().sorted(Comparator.comparingInt(Entry::order)).map(Entry::property).toList();
	}
}
