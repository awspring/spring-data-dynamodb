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

import io.awspring.cloud.dynamodb.repository.ExpressionName;
import io.awspring.cloud.dynamodb.repository.ExpressionValue;
import io.awspring.cloud.dynamodb.repository.Query;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.data.expression.ValueEvaluationContextProvider;
import org.springframework.data.expression.ValueExpression;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.data.repository.query.ValueExpressionQueryRewriter;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
final class QueryExpressions {

	private static final Pattern VALUE_TOKEN = Pattern.compile(":([A-Za-z0-9_]+)");

	private QueryExpressions() {
	}

	record Bound(String expression, Map<String, Object> values) {
	}

	static Bound bind(ValueExpressionDelegate valueExpressionDelegate, Parameters<?, ?> parameters,
			String rawExpression, ParameterAccessor accessor) {

		ValueExpressionQueryRewriter.EvaluatingValueExpressionQueryRewriter rewriter = ValueExpressionQueryRewriter
				.of(valueExpressionDelegate, (index, expression) -> "__spel_" + index, (prefix, name) -> ":" + name);

		ValueExpressionQueryRewriter.QueryExpressionEvaluator evaluator = rewriter.parse(rawExpression, parameters);

		String expression = evaluator.getQueryString();
		Map<String, Object> values = new LinkedHashMap<>();

		Object[] rawArgs = rawArgs(accessor);
		evaluator.evaluate(rawArgs).forEach((name, value) -> values.put(":" + name, value));

		bindPlainPlaceholders(parameters, expression, accessor, values);
		return new Bound(expression, values);
	}

	static void bindPlainPlaceholders(Parameters<?, ?> parameters, String expression, ParameterAccessor accessor,
			Map<String, Object> values) {

		Map<String, Object> byName = new HashMap<>();
		Map<Integer, Object> byPosition = new HashMap<>();

		int bindableIndex = 0;
		for (Parameter parameter : parameters.getBindableParameters()) {
			Object value = accessor.getBindableValue(bindableIndex);
			byPosition.put(bindableIndex, value);
			parameter.getName().ifPresent(name -> byName.put(name, value));
			bindableIndex++;
		}

		Matcher matcher = VALUE_TOKEN.matcher(expression);
		while (matcher.find()) {
			String token = matcher.group(1);
			String key = ":" + token;
			if (values.containsKey(key)) {
				continue;
			}
			if (byName.containsKey(token)) {
				values.put(key, byName.get(token));
			}
			else if (isAllDigits(token)) {
				int position = Integer.parseInt(token);
				if (byPosition.containsKey(position)) {
					values.put(key, byPosition.get(position));
				}
			}
		}
	}

	static Map<String, String> expressionNames(@Nullable Query query) {
		Map<String, String> names = new LinkedHashMap<>();
		if (query != null) {
			for (ExpressionName name : query.names()) {
				names.put(name.name(), name.value());
			}
		}
		return names;
	}

	static void applyExpressionValues(@Nullable Query query, ValueExpressionDelegate valueExpressionDelegate,
			Parameters<?, ?> parameters, ParameterAccessor accessor, Map<String, Object> values) {
		if (query == null || query.values().length == 0) {
			return;
		}
		ValueEvaluationContextProvider contextProvider = valueExpressionDelegate.createValueContextProvider(parameters);
		Object[] rawArgs = rawArgs(accessor);
		for (ExpressionValue value : query.values()) {
			ValueExpression expression = valueExpressionDelegate.parse(value.value());
			values.put(value.name(), expression.evaluate(contextProvider.getEvaluationContext(rawArgs)));
		}
	}

	private static Object[] rawArgs(ParameterAccessor accessor) {
		return ((DynamoDbParametersParameterAccessor) accessor).getValues();
	}

	private static boolean isAllDigits(String token) {
		for (int i = 0; i < token.length(); i++) {
			if (!Character.isDigit(token.charAt(i))) {
				return false;
			}
		}
		return !token.isEmpty();
	}
}
