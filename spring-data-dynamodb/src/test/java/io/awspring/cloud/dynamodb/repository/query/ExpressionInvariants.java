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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.awspring.cloud.dynamodb.repository.query.DynamoDbQuerySpec.SortCondition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ExpressionInvariants {

	private static final Pattern VALUE_REF = Pattern.compile(":[A-Za-z0-9_]+");

	private static final Pattern NAME_REF = Pattern.compile("#[A-Za-z0-9_]+");

	private ExpressionInvariants() {
	}

	// Runs every structural invariant. suppliedArguments are the values handed to the query method,
	// flattened for collection parameters.
	static void assertAllInvariants(DynamoDbQuerySpec spec, Object... suppliedArguments) {
		assertEveryValueReferenceIsBound(spec);
		assertEveryBoundValueIsReferenced(spec);
		assertEveryNameReferenceIsDeclared(spec);
		assertNoDeclaredNameIsUnused(spec);
		assertRangeOperandsAreDistinct(spec);
		assertNoArgumentIsSilentlyDropped(spec, suppliedArguments);
	}

	// Every :slot used in an expression must exist in ExpressionAttributeValues.
	static void assertEveryValueReferenceIsBound(DynamoDbQuerySpec spec) {
		Set<String> bound = spec.expressionAttributeValues().keySet();
		for (String expression : expressionsOf(spec)) {
			for (String reference : referencesIn(VALUE_REF, expression)) {
				assertTrue(bound.contains(reference),
						() -> "expression references " + reference + " but it is not bound in "
								+ "ExpressionAttributeValues -- DynamoDB rejects this with a ValidationException. "
								+ "expression=[" + expression + "] bound=" + bound);
			}
		}
	}

	// Every bound value must actually be referenced. DynamoDB rejects a request carrying an unused
	// ExpressionAttributeValue, so a leftover slot is a hard error, not dead weight.
	static void assertEveryBoundValueIsReferenced(DynamoDbQuerySpec spec) {
		Set<String> referenced = new LinkedHashSet<>();
		for (String expression : expressionsOf(spec)) {
			referenced.addAll(referencesIn(VALUE_REF, expression));
		}
		for (String slot : spec.expressionAttributeValues().keySet()) {
			assertTrue(referenced.contains(slot),
					() -> "ExpressionAttributeValues binds " + slot + " but no expression references it -- "
							+ "DynamoDB rejects unused expression attribute values. referenced=" + referenced);
		}
	}

	// Every #alias used in an expression must exist in ExpressionAttributeNames.
	static void assertEveryNameReferenceIsDeclared(DynamoDbQuerySpec spec) {
		Set<String> declared = spec.expressionAttributeNames().keySet();
		for (String expression : expressionsOf(spec)) {
			for (String reference : referencesIn(NAME_REF, expression)) {
				assertTrue(declared.contains(reference),
						() -> "expression references " + reference + " but it is not declared in "
								+ "ExpressionAttributeNames. expression=[" + expression + "] declared=" + declared);
			}
		}
	}

	// DynamoDB likewise rejects an unused ExpressionAttributeName.
	static void assertNoDeclaredNameIsUnused(DynamoDbQuerySpec spec) {
		Set<String> referenced = new LinkedHashSet<>();
		for (String expression : expressionsOf(spec)) {
			referenced.addAll(referencesIn(NAME_REF, expression));
		}
		for (String alias : spec.expressionAttributeNames().keySet()) {
			assertTrue(referenced.contains(alias),
					() -> "ExpressionAttributeNames declares " + alias + " but no expression references it -- "
							+ "DynamoDB rejects unused expression attribute names. referenced=" + referenced);
		}
	}

	// A two-operand range must compare two different slots. x BETWEEN :p AND :p parses and executes
	// happily against DynamoDB while quietly degenerating into an equality check, so only this
	// structural rule catches it.
	static void assertRangeOperandsAreDistinct(DynamoDbQuerySpec spec) {
		Pattern between = Pattern.compile("(#[A-Za-z0-9_]+)\\s+BETWEEN\\s+(:[A-Za-z0-9_]+)\\s+AND\\s+(:[A-Za-z0-9_]+)");
		for (String expression : expressionsOf(spec)) {
			Matcher matcher = between.matcher(expression);
			while (matcher.find()) {
				String lower = matcher.group(2);
				String upper = matcher.group(3);
				if (lower.equals(upper)) {
					fail("BETWEEN compares the same slot twice (" + lower + ") in [" + expression
							+ "] -- the lower bound has been overwritten, so the range silently collapses to "
							+ "an equality check on the upper bound");
				}
			}
		}
	}

	// No caller-supplied argument may vanish during translation. Every argument has to survive into
	// the partition key, a sort condition, or a bound expression value; anything else means the query
	// executes against DynamoDB while ignoring part of what was asked for.
	static void assertNoArgumentIsSilentlyDropped(DynamoDbQuerySpec spec, Object... suppliedArguments) {
		Collection<Object> reachable = reachableValues(spec);
		for (Object argument : suppliedArguments) {
			assertTrue(containsValue(reachable, argument),
					() -> "argument [" + argument + "] was supplied to the query method but does not appear "
							+ "anywhere in the generated spec -- it has been silently dropped, so DynamoDB would "
							+ "be asked a broader question than the caller asked. reachable=" + reachable);
		}
	}

	// Every value the spec would actually send to DynamoDB.
	static Collection<Object> reachableValues(DynamoDbQuerySpec spec) {
		List<Object> reachable = new ArrayList<>(spec.partitionEquals().values());
		for (SortCondition condition : spec.sortConditions()) {
			reachable.add(condition.value());
			if (condition.rangeEnd() != null) {
				reachable.add(condition.rangeEnd());
			}
		}
		reachable.addAll(spec.expressionAttributeValues().values());
		return reachable;
	}

	private static boolean containsValue(Collection<Object> reachable, Object argument) {
		for (Object candidate : reachable) {
			if (candidate == null) {
				continue;
			}
			if (candidate.equals(argument)) {
				return true;
			}
			// an IN list may be bound either as a whole or element-by-element
			if (candidate instanceof Collection<?> collection && collection.contains(argument)) {
				return true;
			}
		}
		return false;
	}

	// All expression texts a spec can carry.
	private static List<String> expressionsOf(DynamoDbQuerySpec spec) {
		List<String> expressions = new ArrayList<>();
		String filter = spec.filterExpression();
		if (filter != null && !filter.isBlank()) {
			expressions.add(filter);
		}
		String rawKeyCondition = spec.rawKeyConditionExpression();
		if (rawKeyCondition != null && !rawKeyCondition.isBlank()) {
			expressions.add(rawKeyCondition);
		}
		return expressions;
	}

	private static Set<String> referencesIn(Pattern pattern, String expression) {
		Set<String> references = new LinkedHashSet<>();
		Matcher matcher = pattern.matcher(expression);
		while (matcher.find()) {
			references.add(matcher.group());
		}
		return references;
	}

	// Convenience for asserting a spec pins a partition key to an exact value.
	static void assertPartitionPinnedTo(DynamoDbQuerySpec spec, String column, Object value) {
		Map<String, Object> partitionEquals = spec.partitionEquals();
		assertTrue(partitionEquals.containsKey(column),
				() -> "expected the partition key [" + column + "] to be pinned, but only " + partitionEquals.keySet()
						+ " were. An unpinned partition key forces a full-table Scan.");
		assertTrue(value.equals(partitionEquals.get(column)), () -> "partition key [" + column + "] pinned to ["
				+ partitionEquals.get(column) + "], expected [" + value + "]");
	}
}
