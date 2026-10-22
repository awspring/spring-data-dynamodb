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

import io.awspring.spring.data.dynamodb.core.DynamoDbOperations;
import io.awspring.spring.data.dynamodb.repository.Filter;
import io.awspring.spring.data.dynamodb.repository.VectorQuery;
import io.awspring.spring.data.dynamodb.request.DynamoDbVectorSearchRequest;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.Assert;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class VectorSearchQuery extends AbstractVectorSearchQuery {

	private final VectorQuery vectorQuery;
	private final List<FilterBinding> filterBindings;

	public VectorSearchQuery(DynamoDbQueryMethod queryMethod, DynamoDbOperations operations) {
		super(queryMethod, operations, requiredAnnotation(queryMethod).topK());
		this.vectorQuery = requiredAnnotation(queryMethod);
		this.filterBindings = filterBindings(queryMethod.getMethod().getParameters());
	}

	private static VectorQuery requiredAnnotation(DynamoDbQueryMethod queryMethod) {
		VectorQuery annotation = queryMethod.getVectorQueryAnnotation();
		Assert.state(annotation != null, () -> "Query method must have a @VectorQuery annotation: " + queryMethod);
		return annotation;
	}

	@Override
	protected String indexName() {
		return this.vectorQuery.indexName();
	}

	@Override
	protected void applySearchCondition(DynamoDbVectorSearchRequest.Builder builder, Object[] parameters) {
		if (this.filterBindings.isEmpty()) {
			return;
		}
		Map<String, String> names = new LinkedHashMap<>();
		Map<String, Object> values = new LinkedHashMap<>();
		List<String> clauses = new ArrayList<>(this.filterBindings.size());
		for (int i = 0; i < this.filterBindings.size(); i++) {
			FilterBinding binding = this.filterBindings.get(i);
			names.put("#f" + i, binding.attribute());
			values.put(":f" + i, parameters[binding.parameterIndex()]);
			clauses.add("#f" + i + " = :f" + i);
		}
		builder.withSearchConditionExpression(String.join(" AND ", clauses)).withExpressionAttributeNames(names)
				.withExpressionAttributeValues(values);
	}

	private static List<FilterBinding> filterBindings(Parameter[] parameters) {
		List<FilterBinding> bindings = new ArrayList<>();
		for (int i = 0; i < parameters.length; i++) {
			Filter filter = parameters[i].getAnnotation(Filter.class);
			if (filter != null) {
				bindings.add(new FilterBinding(i, filter.value()));
			}
		}
		return List.copyOf(bindings);
	}

	private record FilterBinding(int parameterIndex, String attribute) {
	}
}
