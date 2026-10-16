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
package io.awspring.spring.data.dynamodb.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.awspring.spring.data.dynamodb.core.DynamoDbTemplate;
import io.awspring.spring.data.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.Score;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.core.mapping.VectorIndex;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.repository.query.Param;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.SearchResultItem;
import software.amazon.awssdk.services.dynamodb.model.SearchVectorsRequest;
import software.amazon.awssdk.services.dynamodb.model.SearchVectorsResponse;

@DisplayName("Vector search end to end (repository -> template -> StatementFactory, mocked SDK client)")
class VectorSearchEndToEndTest {

	private static final float[] QUERY_VECTOR = { 0.1f, 0.2f, 0.3f };

	@Table(tableName = "products")
	static class ProductMatch {
		@PartitionKey
		String id;
		String title;
		@VectorIndex(indexName = "DescriptionIndex")
		float[] embedding;
		@Score
		Double score;
	}

	@Table(tableName = "products")
	static class MultiIndexProduct {
		@PartitionKey
		String id;
		@VectorIndex(indexName = "TitleIndex", tableName = "product-titles")
		float[] titleEmbedding;
		@VectorIndex(indexName = "ImageIndex")
		float[] imageEmbedding;
	}

	interface ProductSearchRepository extends VectorSearchRepository<ProductMatch> {

		@VectorQuery(topK = 5)
		List<ProductMatch> findNearestInCategory(float[] queryVector, @Filter("category") String category);

		@VectorQuery(topK = 500)
		List<ProductMatch> findNearestBeyondServiceLimit(float[] queryVector);

		@VectorSearch(topK = 3, searchConditionExpression = "#cat = :cat AND #brand = :brand", names = {
				@ExpressionName(name = "#cat", value = "category"), @ExpressionName(name = "#brand", value = "brand") })
		List<ProductMatch> byCategoryAndBrand(float[] queryVector, @Param("cat") String cat,
				@Param("brand") String brand);

		@VectorQuery(topK = 1)
		Optional<ProductMatch> findNearest(float[] queryVector);

		@VectorQuery(topK = 2)
		Window<ProductMatch> findNearestWindow(float[] queryVector, ScrollPosition position);
	}

	interface MultiIndexSearchRepository extends VectorSearchRepository<MultiIndexProduct> {

		@VectorQuery(topK = 3, indexName = "TitleIndex")
		List<MultiIndexProduct> byTitle(float[] queryVector);

		@VectorQuery(topK = 3, indexName = "ImageIndex")
		List<MultiIndexProduct> byImage(float[] queryVector);
	}

	interface ProductRepository extends DynamoDbRepository<ProductMatch, String> {

		@VectorQuery(topK = 4)
		List<ProductMatch> similar(float[] queryVector);
	}

	interface WritingVectorRepository extends VectorSearchRepository<ProductMatch> {

		@Update(updateExpression = "SET #t = :title", names = @ExpressionName(name = "#t", value = "title"))
		void rename(@Param("id") String id, @Param("title") String title);
	}

	private DynamoDbClient client;
	private DynamoDbRepositoryFactory factory;

	@BeforeEach
	void setUp() {
		this.client = mock(DynamoDbClient.class);
		MappingDynamoDbConverter converter = new MappingDynamoDbConverter(new DynamoDbMappingContext());
		converter.afterPropertiesSet();
		this.factory = new DynamoDbRepositoryFactory(new DynamoDbTemplate(client, converter));
	}

	private void stubResults(SearchResultItem... results) {
		when(client.searchVectors(any(SearchVectorsRequest.class)))
				.thenReturn(SearchVectorsResponse.builder().searchResults(results).build());
	}

	private static SearchResultItem result(String id, String title, double score) {
		return SearchResultItem.builder()
				.item(Map.of("id", AttributeValue.fromS(id), "title", AttributeValue.fromS(title))).score(score)
				.build();
	}

	private SearchVectorsRequest sentRequest() {
		ArgumentCaptor<SearchVectorsRequest> captor = ArgumentCaptor.forClass(SearchVectorsRequest.class);
		verify(client).searchVectors(captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("searchNearest sends table, index, topK and vector, and maps items with their scores in order")
	void searchNearest_mapsItemsAndScores() {
		stubResults(result("p1", "Headphones", 0.01), result("p2", "Speaker", 0.02));

		List<ProductMatch> found = factory.getRepository(ProductSearchRepository.class).searchNearest(QUERY_VECTOR, 2);

		SearchVectorsRequest sent = sentRequest();
		assertAll(() -> assertEquals(2, found.size()), () -> assertEquals("Headphones", found.get(0).title),
				() -> assertEquals(0.01, found.get(0).score), () -> assertEquals("Speaker", found.get(1).title),
				() -> assertEquals(0.02, found.get(1).score), () -> assertEquals("products", sent.tableName()),
				() -> assertEquals("DescriptionIndex", sent.indexName()), () -> assertEquals(2, sent.topK()),
				() -> assertEquals(
						List.of(AttributeValue.fromN("0.1"), AttributeValue.fromN("0.2"), AttributeValue.fromN("0.3")),
						sent.searchVector()),
				() -> assertNull(sent.searchConditionExpression()));
	}

	@Test
	@DisplayName("@VectorQuery compiles each @Filter parameter into an equality condition")
	void vectorQuery_compilesFiltersToEquality() {
		stubResults(result("p1", "Headphones", 0.01));

		factory.getRepository(ProductSearchRepository.class).findNearestInCategory(QUERY_VECTOR, "Electronics");

		SearchVectorsRequest sent = sentRequest();
		assertAll(() -> assertEquals(5, sent.topK()), () -> assertEquals("#f0 = :f0", sent.searchConditionExpression()),
				() -> assertEquals(Map.of("#f0", "category"), sent.expressionAttributeNames()),
				() -> assertEquals(Map.of(":f0", AttributeValue.fromS("Electronics")),
						sent.expressionAttributeValues()));
	}

	@Test
	@DisplayName("@VectorSearch binds :params by @Param name and passes the expression through")
	void vectorSearch_bindsNamedParameters() {
		stubResults(result("p1", "Headphones", 0.01));

		factory.getRepository(ProductSearchRepository.class).byCategoryAndBrand(QUERY_VECTOR, "Electronics", "Acme");

		SearchVectorsRequest sent = sentRequest();
		assertAll(() -> assertEquals(3, sent.topK()),
				() -> assertEquals("#cat = :cat AND #brand = :brand", sent.searchConditionExpression()),
				() -> assertEquals(Map.of("#cat", "category", "#brand", "brand"), sent.expressionAttributeNames()),
				() -> assertEquals(
						Map.of(":cat", AttributeValue.fromS("Electronics"), ":brand", AttributeValue.fromS("Acme")),
						sent.expressionAttributeValues()));
	}

	@Test
	@DisplayName("topK is sent as declared; the service, not the library, enforces its range")
	void topK_isNotValidatedClientSide() {
		stubResults();

		factory.getRepository(ProductSearchRepository.class).findNearestBeyondServiceLimit(QUERY_VECTOR);

		assertEquals(500, sentRequest().topK());
	}

	@Test
	@DisplayName("An Optional return yields the most similar result, or empty when nothing matched")
	void optionalReturn_yieldsFirstResultOrEmpty() {
		ProductSearchRepository repository = factory.getRepository(ProductSearchRepository.class);

		stubResults(result("p1", "Headphones", 0.01), result("p2", "Speaker", 0.02));
		Optional<ProductMatch> nearest = repository.findNearest(QUERY_VECTOR);

		stubResults();
		Optional<ProductMatch> none = repository.findNearest(QUERY_VECTOR);

		assertAll(() -> assertEquals("p1", nearest.orElseThrow().id), () -> assertTrue(none.isEmpty()));
	}

	@Test
	@DisplayName("A Window return holds the result set and reports no next window")
	void windowReturn_hasNoNextWindow() {
		stubResults(result("p1", "Headphones", 0.01), result("p2", "Speaker", 0.02));

		Window<ProductMatch> window = factory.getRepository(ProductSearchRepository.class)
				.findNearestWindow(QUERY_VECTOR, ScrollPosition.keyset());

		assertAll(() -> assertEquals(2, window.size()), () -> assertFalse(window.hasNext()));
	}

	@Test
	@DisplayName("With several @VectorIndex properties, the named index selects its own table")
	void multipleIndexes_selectedByIndexName() {
		stubResults();
		MultiIndexSearchRepository repository = factory.getRepository(MultiIndexSearchRepository.class);

		repository.byTitle(QUERY_VECTOR);
		repository.byImage(QUERY_VECTOR);

		ArgumentCaptor<SearchVectorsRequest> captor = ArgumentCaptor.forClass(SearchVectorsRequest.class);
		verify(client, times(2)).searchVectors(captor.capture());
		List<SearchVectorsRequest> sent = captor.getAllValues();
		assertAll(() -> assertEquals("TitleIndex", sent.get(0).indexName()),
				() -> assertEquals("product-titles", sent.get(0).tableName()),
				() -> assertEquals("ImageIndex", sent.get(1).indexName()),
				() -> assertEquals("products", sent.get(1).tableName()));
	}

	@Test
	@DisplayName("With several @VectorIndex properties and no index name, the search is rejected before any call")
	void multipleIndexes_withoutIndexName_rejected() {
		MultiIndexSearchRepository repository = factory.getRepository(MultiIndexSearchRepository.class);

		InvalidDataAccessApiUsageException exception = assertThrows(InvalidDataAccessApiUsageException.class,
				() -> repository.searchNearest(QUERY_VECTOR, 3));

		assertAll(() -> assertTrue(exception.getMessage().contains("declares 2 @VectorIndex properties")),
				() -> assertTrue(exception.getMessage().contains("TitleIndex")),
				() -> assertTrue(exception.getMessage().contains("ImageIndex")), () -> verifyNoInteractions(client));
	}

	@Test
	@DisplayName("@VectorQuery also works on a base-table DynamoDbRepository")
	void vectorQuery_onDynamoDbRepository() {
		stubResults(result("p1", "Headphones", 0.01));

		List<ProductMatch> found = factory.getRepository(ProductRepository.class).similar(QUERY_VECTOR);

		SearchVectorsRequest sent = sentRequest();
		assertAll(() -> assertEquals(1, found.size()), () -> assertEquals("DescriptionIndex", sent.indexName()),
				() -> assertEquals(4, sent.topK()));
	}

	@Test
	@DisplayName("@Update on a VectorSearchRepository is rejected at bootstrap")
	void update_onVectorSearchRepository_rejected() {
		InvalidDataAccessApiUsageException exception = assertThrows(InvalidDataAccessApiUsageException.class,
				() -> factory.getRepository(WritingVectorRepository.class));

		assertTrue(exception.getMessage().startsWith("@Update is not supported on a VectorSearchRepository"));
	}
}
