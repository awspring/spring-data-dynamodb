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
package io.awspring.spring.data.examples.service.usecase;

import io.awspring.spring.data.examples.model.Product;
import io.awspring.spring.data.examples.repository.ProductRepository;
import io.awspring.spring.data.examples.repository.ProductSearchRepository;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Demonstrates DynamoDB vector search through repositories, with similarity scores. */
@Component
@Order(130)
public class VectorSearchUseCase implements ExampleUseCase {

	private static final float[] HEADPHONES_QUERY = { 0.9f, 0.1f, 0.0f };

	private final ProductRepository products;
	private final ProductSearchRepository productSearch;

	public VectorSearchUseCase(ProductRepository products, ProductSearchRepository productSearch) {
		this.products = products;
		this.productSearch = productSearch;
	}

	@Override
	public String title() {
		return "Vector similarity search";
	}

	@Override
	public void run() {
		products.saveAll(List.of(Product.of("P1", "Wireless headphones", "audio", "acme", 1.0f, 0.0f, 0.0f),
				Product.of("P2", "Noise cancelling earbuds", "audio", "zen", 0.8f, 0.2f, 0.0f),
				Product.of("P3", "Bluetooth speaker", "audio", "acme", 0.5f, 0.5f, 0.0f),
				Product.of("P4", "4K monitor", "video", "acme", 0.0f, 0.0f, 1.0f)));

		print("searchNearest(top 2)", productSearch.searchNearest(HEADPHONES_QUERY, 2));
		print("@VectorQuery category=audio", products.findNearestInCategory(HEADPHONES_QUERY, "audio"));
		print("@VectorSearch category=audio, brand=acme",
				products.findNearestInCategoryAndBrand(HEADPHONES_QUERY, "audio", "acme"));
		products.findMostSimilar(HEADPHONES_QUERY)
				.ifPresent(product -> System.out.println("most similar -> " + describe(product)));
	}

	private static void print(String label, List<Product> found) {
		System.out.println(label + " -> " + found.stream().map(VectorSearchUseCase::describe).toList());
	}

	private static String describe(Product product) {
		return product.getTitle() + " (score " + product.getScore() + ")";
	}
}
