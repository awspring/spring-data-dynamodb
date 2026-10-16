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
package io.awspring.spring.data.dynamodb.request;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Options for a low-level DynamoDB {@code SearchVectors} request.
 *
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DynamoDbVectorSearchRequest {

	private float @Nullable [] vector;
	private @Nullable Integer topK;
	private @Nullable String indexName;
	private @Nullable String searchConditionExpression;
	private @Nullable Map<String, String> expressionAttributeNames;
	private @Nullable Map<String, Object> expressionAttributeValues;
	private @Nullable String projectionExpression;

	public float @Nullable [] getVector() {
		return vector;
	}

	@Nullable
	public Integer getTopK() {
		return topK;
	}

	@Nullable
	public String getIndexName() {
		return indexName;
	}

	@Nullable
	public String getSearchConditionExpression() {
		return searchConditionExpression;
	}

	@Nullable
	public Map<String, String> getExpressionAttributeNames() {
		return expressionAttributeNames;
	}

	@Nullable
	public Map<String, Object> getExpressionAttributeValues() {
		return expressionAttributeValues;
	}

	@Nullable
	public String getProjectionExpression() {
		return projectionExpression;
	}

	public static Builder request() {
		return new Builder();
	}

	public static final class Builder {

		private float @Nullable [] vector;
		private @Nullable Integer topK;
		private @Nullable String indexName;
		private @Nullable String searchConditionExpression;
		private @Nullable Map<String, String> expressionAttributeNames;
		private @Nullable Map<String, Object> expressionAttributeValues;
		private @Nullable String projectionExpression;

		private Builder() {
		}

		public Builder withVector(float @Nullable [] vector) {
			this.vector = vector;
			return this;
		}

		public Builder withTopK(@Nullable Integer topK) {
			this.topK = topK;
			return this;
		}

		public Builder withIndexName(@Nullable String indexName) {
			this.indexName = indexName;
			return this;
		}

		public Builder withSearchConditionExpression(@Nullable String searchConditionExpression) {
			this.searchConditionExpression = searchConditionExpression;
			return this;
		}

		public Builder withExpressionAttributeNames(@Nullable Map<String, String> expressionAttributeNames) {
			this.expressionAttributeNames = expressionAttributeNames;
			return this;
		}

		public Builder withExpressionAttributeValues(@Nullable Map<String, Object> expressionAttributeValues) {
			this.expressionAttributeValues = expressionAttributeValues;
			return this;
		}

		public Builder withProjectionExpression(@Nullable String projectionExpression) {
			this.projectionExpression = projectionExpression;
			return this;
		}

		public DynamoDbVectorSearchRequest build() {
			DynamoDbVectorSearchRequest request = new DynamoDbVectorSearchRequest();
			request.vector = this.vector;
			request.topK = this.topK;
			request.indexName = this.indexName;
			request.searchConditionExpression = this.searchConditionExpression;
			request.expressionAttributeNames = this.expressionAttributeNames;
			request.expressionAttributeValues = this.expressionAttributeValues;
			request.projectionExpression = this.projectionExpression;
			return request;
		}
	}
}
