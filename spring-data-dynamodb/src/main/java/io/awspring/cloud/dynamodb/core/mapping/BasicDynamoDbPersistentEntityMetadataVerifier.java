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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.mapping.MappingException;
import org.springframework.util.StringUtils;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class BasicDynamoDbPersistentEntityMetadataVerifier implements DynamoDbPersistentEntityMetadataVerifier {

	@Override
	public void verify(DynamoDbPersistentEntity<?> entity) throws MappingException {
		if (entity.getType().isInterface()) {
			return;
		}

		boolean hasTable = entity.isAnnotationPresent(Table.class);
		boolean isView = entity.isSecondaryIndexView();
		boolean isAggregate = entity.isAggregateView();

		int categories = (hasTable ? 1 : 0) + (isView ? 1 : 0) + (isAggregate ? 1 : 0);
		if (categories > 1) {
			throw new VerifierMappingExceptions(entity,
					List.of(new MappingException(String.format(
							"%s declares more than one of @Table, @SecondaryIndex and @AggregateTable; a class must be "
									+ "exactly one of: a base-table entity, a read-only secondary-index view, or a "
									+ "read-only aggregate fold",
							entity.getType().getName()))));
		}

		if (isAggregate) {
			verifyAggregate(entity);
			return;
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

		verifyDerivedProperties(entity, exceptions);

		if (!exceptions.isEmpty()) {
			fail(entity, exceptions);
		}
	}

	private static void verifyDerivedProperties(DynamoDbPersistentEntity<?> entity, List<MappingException> exceptions) {

		Set<String> placeholders = new LinkedHashSet<>();
		for (SortKeyTemplate annotation : AnnotatedElementUtils.findMergedRepeatableAnnotations(entity.getType(),
				SortKeyTemplate.class, SortKeyTemplate.List.class)) {
			placeholders.addAll(KeyTemplate.parse(annotation.value()).placeholderNames());
		}

		for (DynamoDbPersistentProperty property : entity) {
			if (!property.isDerived()) {
				continue;
			}
			if (!property.getKeyRoles().isEmpty()) {
				exceptions.add(new MappingException(String.format(
						"%s.%s is annotated @Derived but is a key property; a key attribute must always be written",
						entity.getType().getName(), property.getName())));
				continue;
			}
			if (property.getType().isPrimitive()) {
				exceptions.add(new MappingException(String.format(
						"%s.%s is annotated @Derived but has primitive type %s; use the boxed type so the value can be "
								+ "left unset before it is decomposed on read",
						entity.getType().getName(), property.getName(), property.getType().getName())));
				continue;
			}
			if (!placeholders.contains(property.getName())) {
				exceptions.add(new MappingException(String.format(
						"%s.%s is annotated @Derived but is not a @SortKeyTemplate placeholder, so its value could not "
								+ "be recovered on read; declared placeholders: %s",
						entity.getType().getName(), property.getName(), placeholders)));
			}
		}
	}

	private static void verifyAggregate(DynamoDbPersistentEntity<?> entity) {
		List<MappingException> exceptions = new ArrayList<>();

		AggregateTable annotation = entity.findAnnotation(AggregateTable.class);
		if (annotation == null) {
			return;
		}

		if (!StringUtils.hasText(annotation.tableName())) {
			exceptions.add(new MappingException(
					String.format("%s @AggregateTable.tableName() must not be blank", entity.getType().getName())));
		}
		if (!StringUtils.hasText(annotation.partitionKey())) {
			exceptions.add(new MappingException(
					String.format("%s @AggregateTable.partitionKey() must not be blank", entity.getType().getName())));
		}

		boolean gsiScoped = StringUtils.hasText(annotation.indexName());
		boolean aggregateSortKeyBlank = !StringUtils.hasText(annotation.sortKey());
		if (aggregateSortKeyBlank && !gsiScoped) {
			exceptions.add(new MappingException(String.format(
					"%s @AggregateTable.sortKey() must not be blank for a base-table aggregate; DynamoDB only lets "
							+ "multiple items share a partition key via a composite (partition key + sort key) primary "
							+ "key, so a base-table aggregate requires a sort-key attribute",
					entity.getType().getName())));
		}

		int childrenCount = 0;
		for (DynamoDbPersistentProperty property : entity) {
			if (!property.isAggregateItem()) {
				continue;
			}
			childrenCount++;

			if (aggregateSortKeyBlank && gsiScoped) {
				AggregateItem memberRule = property.getAggregateItem();
				if (memberRule == null || !StringUtils.hasText(memberRule.sortKey())) {
					exceptions.add(new MappingException(String.format(
							"%s.%s must declare its own @AggregateItem.sortKey(); when a GSI-scoped @AggregateTable "
									+ "leaves sortKey() blank, every @AggregateItem must name the column it reads its "
									+ "routing value from",
							entity.getType().getName(), property.getName())));
				}
			}

			Class<?> rowType = property.isCollectionLike() ? (property.getTypeInformation().getComponentType() != null
					? property.getTypeInformation().getComponentType().getType()
					: null) : property.getType();

			if (rowType == null) {
				exceptions.add(new MappingException(
						String.format("%s.%s is annotated @AggregateItem but its List has no resolvable element type",
								entity.getType().getName(), property.getName())));
				continue;
			}

			AggregateItem rule = property.getAggregateItem();
			boolean routed = rule != null && (StringUtils.hasText(rule.regex())
					|| StringUtils.hasText(rule.startsWith()) || StringUtils.hasText(rule.endsWith()));
			if (!routed) {
				exceptions.add(new MappingException(String.format(
						"%s.%s is annotated @AggregateItem but declares none of startsWith/endsWith/regex; an "
								+ "aggregate member must declare a routing pattern",
						entity.getType().getName(), property.getName())));
			}
		}

		if (childrenCount == 0) {
			exceptions.add(new MappingException(
					String.format("%s is an @AggregateTable and must declare at least one @AggregateItem member",
							entity.getType().getName())));
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
