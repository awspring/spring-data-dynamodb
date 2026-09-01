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
package io.awspring.spring.data.dynamodb.repository.query;

import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentProperty;
import io.awspring.spring.data.dynamodb.core.mapping.IndexKeySchema;
import io.awspring.spring.data.dynamodb.core.mapping.KeyTemplate;
import io.awspring.spring.data.dynamodb.core.mapping.KeyTemplateResolver;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbQueryCreator extends AbstractQueryCreator<DynamoDbQuerySpec, DynamoDbQuerySpec> {

	private static final String ALWAYS_FALSE = "attribute_exists(nonexistent_attribute_for_empty_in)";
	private static final String ALWAYS_TRUE = "attribute_not_exists(nonexistent_attribute_for_empty_in)";

	private final PartTree tree;
	private final MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext;
	private final DynamoDbPersistentEntity<?> entity;

	private final DynamoDbQuerySpec spec;

	private int placeholderIndex = 0;

	public DynamoDbQueryCreator(PartTree tree, ParameterAccessor accessor,
			MappingContext<? extends DynamoDbPersistentEntity<?>, DynamoDbPersistentProperty> mappingContext,
			Class<?> domainClass) {

		super(tree, accessor);

		this.tree = tree;
		this.mappingContext = mappingContext;
		this.entity = mappingContext.getRequiredPersistentEntity(domainClass);
		DynamoDbQuerySpec templateSpec = trySortKeyTemplateMatch(tree, this.entity, accessor.iterator());
		this.spec = (templateSpec != null) ? templateSpec : selectIndex(tree, this.entity, accessor.iterator());
	}

	@Nullable
	private DynamoDbQuerySpec trySortKeyTemplateMatch(PartTree tree, DynamoDbPersistentEntity<?> entity,
			Iterator<Object> parameters) {

		List<PartTree.OrPart> orParts = new ArrayList<>();
		tree.forEach(orParts::add);
		if (orParts.size() != 1) {
			return null;
		}

		KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

		List<Part> parts = new ArrayList<>();
		orParts.get(0).forEach(parts::add);

		Set<String> equalityColumns = new LinkedHashSet<>();
		Set<String> equalityProperties = new LinkedHashSet<>();
		for (Part part : parts) {
			if (part.getType() == Part.Type.SIMPLE_PROPERTY) {
				equalityColumns.add(columnNameFor(part));
				equalityProperties.add(propertyNameFor(part));
			}
		}

		IndexKeySchema schema = entity.getKeySchema();
		if (schema.isEmpty()) {
			return null;
		}
		Set<String> partitionColumns = columnNames(schema.partitionKeys());
		if (!equalityColumns.containsAll(partitionColumns)) {
			return null;
		}
		String templateColumn = resolver.baseTableSortKeyColumn();
		if (templateColumn == null) {
			return null;
		}
		String chosenIndex = "";

		KeyTemplate template = resolver.templateFor(templateColumn);
		List<String> placeholderNames = template.placeholderNames();

		int leadingCount = 0;
		for (String placeholder : placeholderNames) {
			if (equalityProperties.contains(placeholder)) {
				leadingCount++;
			}
			else {
				break;
			}
		}
		Set<String> leadingPlaceholders = new LinkedHashSet<>(placeholderNames.subList(0, leadingCount));

		DynamoDbQuerySpec spec = DynamoDbQuerySpec.forIndex(chosenIndex);
		DynamoDbQuerySpec staging = DynamoDbQuerySpec.forScan();
		Map<String, Object> placeholderValues = new LinkedHashMap<>();
		List<String> pendingFilterFragments = new ArrayList<>();

		for (Part part : parts) {
			boolean equality = part.getType() == Part.Type.SIMPLE_PROPERTY;
			String columnName = columnNameFor(part);
			String propertyName = propertyNameFor(part);

			if (equality && partitionColumns.contains(columnName)) {
				spec.partitionEquals().put(columnName, parameters.next());
			}
			else if (equality && leadingPlaceholders.contains(propertyName)) {
				placeholderValues.put(propertyName, parameters.next());
			}
			else {
				pendingFilterFragments.add(consumeAsFilterFragment(part, parameters, staging));
			}
		}

		spec.filterFragments().addAll(pendingFilterFragments);
		spec.expressionAttributeNames().putAll(staging.expressionAttributeNames());
		spec.expressionAttributeValues().putAll(staging.expressionAttributeValues());

		if (!placeholderValues.isEmpty()) {
			String sortColumn = resolver.columnFor(templateColumn);
			spec.sortConditionIsTemplateColumn(true);
			boolean allPlaceholdersBound = placeholderValues.size() == placeholderNames.size();
			if (allPlaceholdersBound) {
				Object composedKey = placeholderValues.containsValue(null) ? null : template.compose(placeholderValues);
				spec.sortConditions().add(new DynamoDbQuerySpec.SortCondition(sortColumn,
						DynamoDbQuerySpec.SortCondition.Op.EQ, composedKey, null));
			}
			else {
				spec.sortConditions().add(new DynamoDbQuerySpec.SortCondition(sortColumn,
						DynamoDbQuerySpec.SortCondition.Op.BEGINS_WITH, template.prefixFor(placeholderValues), null));
			}
		}

		return spec;
	}

	private String propertyNameFor(Part part) {
		return mappingContext.getPersistentPropertyPath(part.getProperty()).getLeafProperty().getName();
	}

	private DynamoDbQuerySpec selectIndex(PartTree tree, DynamoDbPersistentEntity<?> entity,
			Iterator<Object> parameters) {

		List<PartTree.OrPart> orParts = new ArrayList<>();
		tree.forEach(orParts::add);

		if (orParts.size() > 1) {
			DynamoDbQuerySpec scanSpec = DynamoDbQuerySpec.forScan();
			if (entity.isSecondaryIndexView()) {
				scanSpec.scanIndexName(entity.getIndexName());
			}
			List<String> branchFragments = new ArrayList<>();
			for (PartTree.OrPart orPart : orParts) {
				List<String> branchParts = new ArrayList<>();
				for (Part part : orPart) {
					branchParts.add(consumeAsFilterFragment(part, parameters, scanSpec));
				}
				branchFragments.add(
						branchParts.size() == 1 ? branchParts.get(0) : "(" + String.join(" AND ", branchParts) + ")");
			}
			scanSpec.filterFragments().add("(" + String.join(" OR ", branchFragments) + ")");
			return scanSpec;
		}

		List<Atom> equalityAtoms = new ArrayList<>();
		List<FixedFragment> fixedFilterFragments = new ArrayList<>();
		PendingSortRange pendingSortRange = null;

		if (!orParts.isEmpty()) {
			for (Part part : orParts.get(0)) {
				if (part.getType() == Part.Type.SIMPLE_PROPERTY) {
					equalityAtoms.add(bindEqualityAtom(part, parameters));
					continue;
				}

				DynamoDbQuerySpec.SortCondition.Op rangeOp = rangeOpFor(part.getType());
				if (rangeOp != null && pendingSortRange == null
						&& isLeadingSortKeyColumn(entity, columnNameFor(part))) {
					pendingSortRange = bindSortRange(part, rangeOp, parameters);
					continue;
				}

				DynamoDbQuerySpec staging = DynamoDbQuerySpec.forScan();
				String fragment = consumeAsFilterFragment(part, parameters, staging);
				fixedFilterFragments.add(new FixedFragment(fragment, staging.expressionAttributeNames(),
						staging.expressionAttributeValues()));
			}
		}

		DynamoDbQuerySpec spec = selectCandidateIndex(entity, equalityAtoms, pendingSortRange);

		for (FixedFragment fragment : fixedFilterFragments) {
			spec.filterFragments().add(fragment.text());
			spec.expressionAttributeNames().putAll(fragment.names());
			spec.expressionAttributeValues().putAll(fragment.values());
		}

		return spec;
	}

	private record FixedFragment(String text, Map<String, String> names, Map<String, Object> values) {
	}

	private DynamoDbQuerySpec selectCandidateIndex(DynamoDbPersistentEntity<?> entity, List<Atom> equalityAtoms,
			@Nullable PendingSortRange pendingSortRange) {

		Map<String, Atom> atomsByColumn = new LinkedHashMap<>();
		for (Atom atom : equalityAtoms) {
			atomsByColumn.put(atom.columnName(), atom);
		}

		IndexKeySchema schema = entity.getKeySchema();
		String indexName = entity.isSecondaryIndexView() ? entity.getIndexName() : "";

		if (!schema.isEmpty()) {
			Set<String> partitionColumns = columnNames(schema.partitionKeys());
			if (atomsByColumn.keySet().containsAll(partitionColumns)) {

				DynamoDbQuerySpec spec = DynamoDbQuerySpec.forIndex(indexName);
				Set<String> consumedColumns = new HashSet<>();

				for (String partitionColumn : partitionColumns) {
					Atom atom = atomsByColumn.get(partitionColumn);
					spec.partitionEquals().put(partitionColumn, atom.value());
					consumedColumns.add(partitionColumn);
				}

				for (DynamoDbPersistentProperty sortKeyProperty : schema.sortKeys()) {
					String sortColumn = sortKeyProperty.getColumnName();
					Atom atom = atomsByColumn.get(sortColumn);
					if (atom == null) {
						break;
					}
					spec.sortConditions().add(new DynamoDbQuerySpec.SortCondition(sortColumn,
							DynamoDbQuerySpec.SortCondition.Op.EQ, atom.value(), null));
					consumedColumns.add(sortColumn);
				}

				if (pendingSortRange != null) {
					if (consumedColumns.contains(pendingSortRange.columnName())) {
						addFragment(spec, pendingSortRange.fallback());
					}
					else {
						spec.sortConditions().add(new DynamoDbQuerySpec.SortCondition(pendingSortRange.columnName(),
								pendingSortRange.op(), pendingSortRange.value(), pendingSortRange.rangeEnd()));
						consumedColumns.add(pendingSortRange.columnName());
					}
				}

				for (Atom atom : equalityAtoms) {
					if (!consumedColumns.contains(atom.columnName())) {
						spec.filterFragments().add(atom.toFilterFragment());
						spec.expressionAttributeNames().put(atom.namePlaceholder(), atom.columnName());
						spec.expressionAttributeValues().put(atom.valuePlaceholder(), atom.value());
					}
				}

				return spec;
			}
		}

		DynamoDbQuerySpec scanSpec = DynamoDbQuerySpec.forScan();
		if (entity.isSecondaryIndexView()) {
			scanSpec.scanIndexName(entity.getIndexName());
		}
		for (Atom atom : equalityAtoms) {
			scanSpec.filterFragments().add(atom.toFilterFragment());
			scanSpec.expressionAttributeNames().put(atom.namePlaceholder(), atom.columnName());
			scanSpec.expressionAttributeValues().put(atom.valuePlaceholder(), atom.value());
		}
		if (pendingSortRange != null) {
			addFragment(scanSpec, pendingSortRange.fallback());
		}
		return scanSpec;
	}

	private static void addFragment(DynamoDbQuerySpec spec, FixedFragment fragment) {
		spec.filterFragments().add(fragment.text());
		spec.expressionAttributeNames().putAll(fragment.names());
		spec.expressionAttributeValues().putAll(fragment.values());
	}

	private record PendingSortRange(String columnName, DynamoDbQuerySpec.SortCondition.Op op, Object value,
			@Nullable Object rangeEnd, FixedFragment fallback) {
	}

	private static DynamoDbQuerySpec.SortCondition.@Nullable Op rangeOpFor(Part.Type type) {
		return switch (type) {
		case GREATER_THAN, AFTER -> DynamoDbQuerySpec.SortCondition.Op.GT;
		case GREATER_THAN_EQUAL -> DynamoDbQuerySpec.SortCondition.Op.GE;
		case LESS_THAN, BEFORE -> DynamoDbQuerySpec.SortCondition.Op.LT;
		case LESS_THAN_EQUAL -> DynamoDbQuerySpec.SortCondition.Op.LE;
		case BETWEEN -> DynamoDbQuerySpec.SortCondition.Op.BETWEEN;
		case STARTING_WITH -> DynamoDbQuerySpec.SortCondition.Op.BEGINS_WITH;
		default -> null;
		};
	}

	private boolean isLeadingSortKeyColumn(DynamoDbPersistentEntity<?> entity, String columnName) {
		IndexKeySchema schema = entity.getKeySchema();
		if (schema.isEmpty()) {
			return false;
		}
		List<DynamoDbPersistentProperty> sortKeys = schema.sortKeys();
		return !sortKeys.isEmpty() && sortKeys.get(0).getColumnName().equals(columnName);
	}

	private PendingSortRange bindSortRange(Part part, DynamoDbQuerySpec.SortCondition.Op op,
			Iterator<Object> parameters) {

		String columnName = columnNameFor(part);
		String namePlaceholder = "#p" + placeholderIndex;
		String valueSlot = ":p" + placeholderIndex;
		placeholderIndex++;

		Object value = parameters.next();
		Object rangeEnd = (op == DynamoDbQuerySpec.SortCondition.Op.BETWEEN) ? parameters.next() : null;

		Map<String, String> names = new LinkedHashMap<>();
		names.put(namePlaceholder, columnName);
		Map<String, Object> values = new LinkedHashMap<>();
		values.put(valueSlot, value);

		String text = switch (op) {
			case GT -> namePlaceholder + " > " + valueSlot;
			case GE -> namePlaceholder + " >= " + valueSlot;
			case LT -> namePlaceholder + " < " + valueSlot;
			case LE -> namePlaceholder + " <= " + valueSlot;
			case BEGINS_WITH -> "begins_with(" + namePlaceholder + ", " + valueSlot + ")";
			case BETWEEN -> {
				String endSlot = valueSlot + "_1";
				values.put(endSlot, rangeEnd);
				yield namePlaceholder + " BETWEEN " + valueSlot + " AND " + endSlot;
			}
			case EQ -> namePlaceholder + " = " + valueSlot;
		};

		return new PendingSortRange(columnName, op, value, rangeEnd, new FixedFragment(text, names, values));
	}

	private static Set<String> columnNames(List<DynamoDbPersistentProperty> properties) {
		Set<String> names = new LinkedHashSet<>();
		for (DynamoDbPersistentProperty property : properties) {
			names.add(property.getColumnName());
		}
		return names;
	}

	private record Atom(String columnName, Object value, String namePlaceholder, String valuePlaceholder) {
		String toFilterFragment() {
			return namePlaceholder + " = " + valuePlaceholder;
		}
	}

	private Atom bindEqualityAtom(Part part, Iterator<Object> parameters) {
		String columnName = columnNameFor(part);
		Object value = parameters.next();
		String namePlaceholder = "#p" + placeholderIndex;
		String valuePlaceholder = ":p" + placeholderIndex;
		placeholderIndex++;
		return new Atom(columnName, value, namePlaceholder, valuePlaceholder);
	}

	private String consumeAsFilterFragment(Part part, Iterator<Object> parameters, DynamoDbQuerySpec target) {

		String columnName = columnNameFor(part);
		String namePlaceholder = "#p" + placeholderIndex;
		target.expressionAttributeNames().put(namePlaceholder, columnName);
		String valueSlot = "p" + placeholderIndex;
		placeholderIndex++;

		return switch (part.getType()) {
			case SIMPLE_PROPERTY -> namePlaceholder + " = " + nextValuePlaceholder(valueSlot, parameters, target);
			case NEGATING_SIMPLE_PROPERTY ->
					namePlaceholder + " <> " + nextValuePlaceholder(valueSlot, parameters, target);
			case GREATER_THAN, AFTER -> namePlaceholder + " > " + nextValuePlaceholder(valueSlot, parameters, target);
			case GREATER_THAN_EQUAL -> namePlaceholder + " >= " + nextValuePlaceholder(valueSlot, parameters, target);
			case LESS_THAN, BEFORE -> namePlaceholder + " < " + nextValuePlaceholder(valueSlot, parameters, target);
			case LESS_THAN_EQUAL -> namePlaceholder + " <= " + nextValuePlaceholder(valueSlot, parameters, target);
			case BETWEEN -> {
				String start = nextValuePlaceholder(valueSlot, parameters, target);
				String end = nextValuePlaceholder(valueSlot, parameters, target);
				yield namePlaceholder + " BETWEEN " + start + " AND " + end;
			}
			case STARTING_WITH -> "begins_with(" + namePlaceholder + ", "
					+ nextValuePlaceholder(valueSlot, parameters, target) + ")";
			case CONTAINING ->
					"contains(" + namePlaceholder + ", " + nextValuePlaceholder(valueSlot, parameters, target) + ")";
			case NOT_CONTAINING -> "NOT contains(" + namePlaceholder + ", "
					+ nextValuePlaceholder(valueSlot, parameters, target) + ")";
			case IN -> inFragment(namePlaceholder, valueSlot, parameters, target, false);
			case NOT_IN -> inFragment(namePlaceholder, valueSlot, parameters, target, true);
			case TRUE -> namePlaceholder + " = " + literalPlaceholder(valueSlot, "true", Boolean.TRUE, target);
			case FALSE -> namePlaceholder + " = " + literalPlaceholder(valueSlot, "false", Boolean.FALSE, target);
			case IS_NULL -> "attribute_not_exists(" + namePlaceholder + ")";
			case IS_NOT_NULL -> "attribute_exists(" + namePlaceholder + ")";
			case IS_EMPTY, IS_NOT_EMPTY ->
					throw reject(part, "size()/empty checks have no DynamoDB filter-expression equivalent");

			case ENDING_WITH, REGEX, LIKE, NOT_LIKE ->
					throw reject(part, "no DynamoDB filter-expression equivalent");
			case NEAR, WITHIN ->
					throw reject(part, "not a meaningful construct for DynamoDB");

			default -> throw reject(part, "not yet classified in this skeleton");
		};
	}

	private String nextValuePlaceholder(String valueSlot, Iterator<Object> parameters, DynamoDbQuerySpec target) {
		String placeholder = ":" + valueSlot;
		int occurrence = 1;
		while (target.expressionAttributeValues().containsKey(placeholder)) {
			placeholder = ":" + valueSlot + "_" + occurrence;
			occurrence++;
		}
		target.expressionAttributeValues().put(placeholder, parameters.next());
		return placeholder;
	}

	private String inFragment(String namePlaceholder, String valueSlot, Iterator<Object> parameters,
			DynamoDbQuerySpec target, boolean negated) {

		Object bound = parameters.next();
		List<Object> elements = toElementList(bound);

		if (elements.isEmpty()) {
			target.expressionAttributeNames().remove(namePlaceholder);
			return negated ? ALWAYS_TRUE : ALWAYS_FALSE;
		}

		List<String> placeholders = new ArrayList<>(elements.size());
		for (int i = 0; i < elements.size(); i++) {
			String placeholder = ":" + valueSlot + indexSuffix(i);
			target.expressionAttributeValues().put(placeholder, elements.get(i));
			placeholders.add(placeholder);
		}
		String in = namePlaceholder + " IN (" + String.join(", ", placeholders) + ")";
		return negated ? "NOT (" + in + ")" : in;
	}

	private static List<Object> toElementList(@Nullable Object bound) {
		if (bound == null) {
			return List.of();
		}
		if (bound instanceof Collection<?> collection) {
			return new ArrayList<>(collection);
		}
		if (bound.getClass().isArray()) {
			int length = Array.getLength(bound);
			List<Object> elements = new ArrayList<>(length);
			for (int i = 0; i < length; i++) {
				elements.add(Array.get(bound, i));
			}
			return elements;
		}
		return List.of(bound);
	}

	private static String indexSuffix(int index) {
		StringBuilder suffix = new StringBuilder();
		int n = index;
		do {
			suffix.insert(0, (char) ('a' + (n % 26)));
			n = n / 26 - 1;
		}
		while (n >= 0);
		return suffix.toString();
	}

	private String literalPlaceholder(String valueSlot, String suffix, Object literalValue, DynamoDbQuerySpec target) {
		String placeholder = ":" + valueSlot + suffix;
		target.expressionAttributeValues().put(placeholder, literalValue);
		return placeholder;
	}

	private InvalidDataAccessApiUsageException reject(Part part, String reason) {
		return new InvalidDataAccessApiUsageException("Unsupported keyword '" + part.getType() + "' on '"
				+ part.getProperty() + "' (" + reason + "). Query: " + tree);
	}

	private String columnNameFor(Part part) {
		var path = mappingContext.getPersistentPropertyPath(part.getProperty());
		return path.getLeafProperty().getColumnName();
	}

	@Override
	protected DynamoDbQuerySpec create(Part part, Iterator<Object> iterator) {
		return this.spec;
	}

	@Override
	protected DynamoDbQuerySpec and(Part part, DynamoDbQuerySpec base, Iterator<Object> iterator) {
		return base;
	}

	@Override
	protected DynamoDbQuerySpec or(DynamoDbQuerySpec base, DynamoDbQuerySpec criteria) {
		return base;
	}

	@Override
	protected DynamoDbQuerySpec complete(@Nullable DynamoDbQuerySpec criteria, Sort sort) {
		if (sort.isSorted()) {
			validateAndApplySort(sort);
		}
		return this.spec;
	}

	private void validateAndApplySort(Sort sort) {
		List<Sort.Order> orders = sort.toList();
		if (orders.size() > 1) {
			throw rejectSort("DynamoDB supports a single ScanIndexForward direction per query, but " + orders.size()
					+ " OrderBy properties were requested " + orders.stream().map(Sort.Order::getProperty).toList()
					+ " -- at most one sort property is supported");
		}

		Sort.Order order = orders.get(0);
		String sortProperty = order.getProperty();

		if (this.spec.requiresScan()) {
			throw rejectSort("sorts by '" + sortProperty + "' but no index serves the predicate as a key "
					+ "condition, so it falls back to a Scan -- DynamoDB Scan does not support ordering");
		}

		if (this.spec.sortConditionIsTemplateColumn()) {
			throw rejectSort("sorts by '" + sortProperty + "' but " + describeIndex(this.spec.indexName())
					+ " uses a @SortKeyTemplate composed sort key (a single opaque string); ordering by a logical "
					+ "placeholder property is not soundly expressible via ScanIndexForward");
		}

		IndexKeySchema schema = this.entity.getKeySchema();
		List<DynamoDbPersistentProperty> sortKeys = schema.sortKeys();
		if (sortKeys.isEmpty()) {
			throw rejectSort("sorts by '" + sortProperty + "' but " + describeIndex(this.spec.indexName())
					+ " has no sort key to order on");
		}
		DynamoDbPersistentProperty leadingSortKey = sortKeys.get(0);
		if (!leadingSortKey.getName().equals(sortProperty)) {
			throw rejectSort("sorts by '" + sortProperty + "' but " + describeIndex(this.spec.indexName())
					+ " orders on its sort key '" + leadingSortKey.getName() + "'");
		}

		this.spec.scanIndexForward(order.isAscending());
	}

	private String describeIndex(@Nullable String indexName) {
		return (indexName == null || indexName.isEmpty()) ? "the base table" : "GSI '" + indexName + "'";
	}

	private InvalidDataAccessApiUsageException rejectSort(String reason) {
		return new InvalidDataAccessApiUsageException("Invalid OrderBy: method " + reason
				+ " (OrderBy: only on the queried " + "index's sort key). Query: " + tree);
	}
}
