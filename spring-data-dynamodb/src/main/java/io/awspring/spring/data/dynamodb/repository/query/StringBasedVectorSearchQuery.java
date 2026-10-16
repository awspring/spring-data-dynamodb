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
import io.awspring.spring.data.dynamodb.repository.VectorSearch;
import io.awspring.spring.data.dynamodb.request.DynamoDbVectorSearchRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class StringBasedVectorSearchQuery extends AbstractVectorSearchQuery {

	private final ValueExpressionDelegate valueExpressionDelegate;
	private final VectorSearch vectorSearch;

	public StringBasedVectorSearchQuery(DynamoDbQueryMethod queryMethod, DynamoDbOperations operations,
			ValueExpressionDelegate valueExpressionDelegate) {
		super(queryMethod, operations, requiredAnnotation(queryMethod).topK());
		Assert.notNull(valueExpressionDelegate, "valueExpressionDelegate must not be null");
		this.valueExpressionDelegate = valueExpressionDelegate;
		this.vectorSearch = requiredAnnotation(queryMethod);
	}

	private static VectorSearch requiredAnnotation(DynamoDbQueryMethod queryMethod) {
		VectorSearch annotation = queryMethod.getVectorSearchAnnotation();
		Assert.state(annotation != null, () -> "Query method must have a @VectorSearch annotation: " + queryMethod);
		return annotation;
	}

	@Override
	protected String indexName() {
		return this.vectorSearch.indexName();
	}

	@Override
	protected void applySearchCondition(DynamoDbVectorSearchRequest.Builder builder, Object[] parameters) {
		if (!StringUtils.hasText(this.vectorSearch.searchConditionExpression())) {
			return;
		}
		DynamoDbQueryMethod queryMethod = getQueryMethod();
		DynamoDbParametersParameterAccessor accessor = new DynamoDbParametersParameterAccessor(queryMethod, parameters);
		QueryExpressions.Bound bound = QueryExpressions.bind(this.valueExpressionDelegate, queryMethod.getParameters(),
				this.vectorSearch.searchConditionExpression(), accessor);
		Map<String, String> names = QueryExpressions.expressionNames(this.vectorSearch);
		Map<String, Object> values = new LinkedHashMap<>(bound.values());
		QueryExpressions.applyExpressionValues(this.vectorSearch, this.valueExpressionDelegate,
				queryMethod.getParameters(), accessor, values);

		builder.withSearchConditionExpression(bound.expression());
		if (!names.isEmpty()) {
			builder.withExpressionAttributeNames(names);
		}
		if (!values.isEmpty()) {
			builder.withExpressionAttributeValues(values);
		}
	}
}
