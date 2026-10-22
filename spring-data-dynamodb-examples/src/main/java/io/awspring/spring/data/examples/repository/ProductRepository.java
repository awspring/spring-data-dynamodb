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
package io.awspring.spring.data.examples.repository;

import io.awspring.spring.data.dynamodb.repository.DynamoDbRepository;
import io.awspring.spring.data.dynamodb.repository.ExpressionName;
import io.awspring.spring.data.dynamodb.repository.Filter;
import io.awspring.spring.data.dynamodb.repository.VectorQuery;
import io.awspring.spring.data.dynamodb.repository.VectorSearch;
import io.awspring.spring.data.examples.model.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Writes products and runs typed and string-based vector searches over their embeddings. */
@Repository
public interface ProductRepository extends DynamoDbRepository<Product, String> {

	@VectorQuery(topK = 2)
	List<Product> findTopTwoNearest(float[] queryVector);

	@VectorQuery(topK = 3)
	List<Product> findNearestInCategory(float[] queryVector, @Filter("category") String category);

	@VectorSearch(topK = 3, searchConditionExpression = "#category = :category AND #brand = :brand", names = {
			@ExpressionName(name = "#category", value = "category"),
			@ExpressionName(name = "#brand", value = "brand") })
	List<Product> findNearestInCategoryAndBrand(float[] queryVector, @Param("category") String category,
			@Param("brand") String brand);

	@VectorQuery(topK = 1)
	Optional<Product> findMostSimilar(float[] queryVector);
}
