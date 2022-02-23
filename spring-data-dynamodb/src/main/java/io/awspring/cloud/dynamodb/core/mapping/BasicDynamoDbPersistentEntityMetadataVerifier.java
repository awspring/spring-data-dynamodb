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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.mapping.MappingException;

public class BasicDynamoDbPersistentEntityMetadataVerifier implements DynamoDbPersistentEntityMetadataVerifier {

	@Override
	public void verify(DynamoDbPersistentEntity<?> entity) throws MappingException {
		if (entity.getType().isInterface()) {
			return;
		}

		boolean hasTable = entity.isAnnotationPresent(Table.class);
		boolean isView = entity.isSecondaryIndexView();

		if (hasTable && isView) {
			throw new VerifierMappingExceptions(entity,
					List.of(new MappingException(String.format(
							"%s declares both @Table and @SecondaryIndex; a class is either a base-table entity "
									+ "(@Table) or a read-only secondary-index view (@SecondaryIndex), never both",
							entity.getType().getName()))));
		}

		if (isView) {
			verifyView(entity);
			return;
		}

		if (!hasTable) {
			return;
		}

		List<MappingException> exceptions = new ArrayList<>();

		if (entity.getIdProperty() == null) {
			exceptions.add(new MappingException(String
					.format("%s must declare exactly one @Id / @PartitionKey property", entity.getType().getName())));
		}

		IndexKeySchema baseTableSchema = entity.getKeySchema();
		if (baseTableSchema.sortKeys().size() > 1) {
			exceptions.add(new MappingException(String.format("%s base table must have at most one sort key; found %d",
					entity.getType().getName(), baseTableSchema.sortKeys().size())));
		}

		if (!exceptions.isEmpty()) {
			fail(entity, exceptions);
		}
	}

	private static void verifyView(DynamoDbPersistentEntity<?> entity) {
		List<MappingException> exceptions = new ArrayList<>();

		List<Integer> partitionOrders = new ArrayList<>();
		List<Integer> sortOrders = new ArrayList<>();
		for (DynamoDbPersistentProperty property : entity) {
			for (KeyRole role : property.getKeyRoles()) {
				if (role.keyType() == KeyRole.KeyType.PARTITION) {
					partitionOrders.add(role.order());
				}
				else {
					sortOrders.add(role.order());
				}
			}
		}

		if (partitionOrders.isEmpty()) {
			exceptions.add(new MappingException(
					String.format("%s is a @SecondaryIndex view and must declare 1-4 @PartitionKey attributes; found 0",
							entity.getType().getName())));
		}
		else if (partitionOrders.size() > 4) {
			exceptions.add(new MappingException(
					String.format("%s @SecondaryIndex view may declare at most 4 @PartitionKey attributes; found %d",
							entity.getType().getName(), partitionOrders.size())));
		}
		else {
			checkContiguous(entity, "partition key", partitionOrders, exceptions);
		}

		if (sortOrders.size() > 4) {
			exceptions.add(new MappingException(
					String.format("%s @SecondaryIndex view may declare at most 4 @SortKey attributes; found %d",
							entity.getType().getName(), sortOrders.size())));
		}
		else if (!sortOrders.isEmpty()) {
			checkContiguous(entity, "sort key", sortOrders, exceptions);
		}

		if (!AnnotatedElementUtils
				.findMergedRepeatableAnnotations(entity.getType(), SortKeyTemplate.class, SortKeyTemplate.List.class)
				.isEmpty()) {
			exceptions.add(new MappingException(String
					.format("%s is a @SecondaryIndex view and must not declare @SortKeyTemplate; a template composes a "
							+ "written attribute and views are read-only", entity.getType().getName())));
		}

		if (!exceptions.isEmpty()) {
			fail(entity, exceptions);
		}
	}

	private static void checkContiguous(DynamoDbPersistentEntity<?> entity, String label, List<Integer> orders,
			List<MappingException> exceptions) {
		List<Integer> sorted = new ArrayList<>(orders);
		Collections.sort(sorted);
		for (int i = 0; i < sorted.size(); i++) {
			if (sorted.get(i) != i) {
				exceptions.add(new MappingException(String.format(
						"%s @SecondaryIndex view %s attributes must use contiguous order() starting at 0 with no "
								+ "gaps or duplicates; found orders %s",
						entity.getType().getName(), label, orders)));
				return;
			}
		}
	}

	private static void fail(DynamoDbPersistentEntity<?> entity, List<MappingException> exceptions) {
		throw new VerifierMappingExceptions(entity, exceptions);
	}
}
