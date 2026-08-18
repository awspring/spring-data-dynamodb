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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.mapping.MappingException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public final class TypeDiscriminatorRegistry {

	private final String discriminatorColumn;
	private final Map<String, Class<?>> classByTag;

	private TypeDiscriminatorRegistry(String discriminatorColumn, Map<String, Class<?>> classByTag) {
		this.discriminatorColumn = discriminatorColumn;
		this.classByTag = classByTag;
	}

	public static TypeDiscriminatorRegistry fromEntities(Collection<? extends DynamoDbPersistentEntity<?>> entities) {
		String discriminatorColumn = null;
		Map<String, Class<?>> classByTag = new LinkedHashMap<>();
		for (DynamoDbPersistentEntity<?> entity : entities) {
			String column = entity.getDiscriminatorColumn();
			if (!column.isEmpty()) {
				if (discriminatorColumn == null) {
					discriminatorColumn = column;
				}
				else if (!discriminatorColumn.equals(column)) {
					throw new MappingException("Entities registered for one table disagree on the "
							+ "discriminator column: '" + discriminatorColumn + "' vs '" + column + "' (from "
							+ entity.getType().getName() + "); all entities sharing a table must "
							+ "use the same @Table(discriminator=...) column name");
				}
			}
			classByTag.put(entity.getTypeName(), entity.getType());
		}
		if (discriminatorColumn == null) {
			throw new MappingException("A class-less read requires at least one entity registered for this "
					+ "table to opt into a discriminator via @Table(discriminator=\"...\"); none of "
					+ classByTag.values() + " did. Without an explicit discriminator, use a typed read "
					+ "(query/scan with a Class<T> or a typed @SecondaryIndex view) instead.");
		}
		return new TypeDiscriminatorRegistry(discriminatorColumn, classByTag);
	}

	public String discriminatorColumn() {
		return discriminatorColumn;
	}

	public Class<?> resolve(String tag) {
		Class<?> type = classByTag.get(tag);
		if (type == null) {
			throw new MappingException(
					"Unknown type-discriminator tag '" + tag + "'; registered tags: " + classByTag.keySet());
		}
		return type;
	}

	public Class<?> resolve(Map<String, AttributeValue> item) {
		AttributeValue tagValue = item.get(discriminatorColumn);
		if (tagValue != null && tagValue.s() != null) {
			Class<?> byTag = classByTag.get(tagValue.s());
			if (byTag != null) {
				return byTag;
			}
		}
		throw new MappingException("Cannot resolve entity type for item " + item.keySet() + ": no '"
				+ discriminatorColumn + "' discriminator matched a registered type " + classByTag.keySet() + ".");
	}
}
