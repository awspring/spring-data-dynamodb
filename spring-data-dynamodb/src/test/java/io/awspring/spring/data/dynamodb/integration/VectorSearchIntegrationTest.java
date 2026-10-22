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
package io.awspring.spring.data.dynamodb.integration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.FlociTestContainer;
import io.awspring.spring.data.dynamodb.config.AbstractDynamoDbConfiguration;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.Score;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.core.mapping.VectorIndex;
import io.awspring.spring.data.dynamodb.exceptions.UncategorizedDynamoDbException;
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepository;
import io.awspring.spring.data.dynamodb.repository.ExpressionName;
import io.awspring.spring.data.dynamodb.repository.Filter;
import io.awspring.spring.data.dynamodb.repository.VectorQuery;
import io.awspring.spring.data.dynamodb.repository.VectorSearch;
import io.awspring.spring.data.dynamodb.repository.config.EnableDynamoDbRepositories;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.repository.query.Param;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.SearchSchemaElementType;
import software.amazon.awssdk.services.dynamodb.model.VectorDistanceFunction;

@DisplayName("Vector search against Floci")
public class VectorSearchIntegrationTest extends FlociTestContainer {

	private static final String TABLE_NAME = "vector_search_it";
	private static final String INDEX_NAME = "EmbeddingIndex";
	private static final float[] QUERY_VECTOR = { 1f, 0f, 0f };

	private AnnotationConfigApplicationContext context;
	private DocRepository docs;

	@Table(tableName = TABLE_NAME)
	public static class Doc {

		@PartitionKey
		private String docId;

		private String category;

		private String brand;

		@VectorIndex(indexName = INDEX_NAME)
		private float[] embedding;

		@Score
		private Double score;

		public Doc() {
		}

		Doc(String docId, String category, String brand, float... embedding) {
			this.docId = docId;
			this.category = category;
			this.brand = brand;
			this.embedding = embedding;
		}

		public String getDocId() {
			return docId;
		}

		public void setDocId(String docId) {
			this.docId = docId;
		}

		public String getCategory() {
			return category;
		}

		public void setCategory(String category) {
			this.category = category;
		}

		public String getBrand() {
			return brand;
		}

		public void setBrand(String brand) {
			this.brand = brand;
		}

		public float[] getEmbedding() {
			return embedding;
		}

		public void setEmbedding(float[] embedding) {
			this.embedding = embedding;
		}

		public Double getScore() {
			return score;
		}

		public void setScore(Double score) {
			this.score = score;
		}
	}

	public interface DocRepository extends DynamoDbRepository<Doc, String> {

		@VectorQuery(topK = 5)
		List<Doc> findNearestInCategory(float[] queryVector, @Filter("category") String category);

		@VectorSearch(topK = 5, searchConditionExpression = "#c = :c AND #b = :b", names = {
				@ExpressionName(name = "#c", value = "category"), @ExpressionName(name = "#b", value = "brand") })
		List<Doc> inCategoryAndBrand(float[] queryVector, @Param("c") String category, @Param("b") String brand);

		@VectorSearch(topK = 5, searchConditionExpression = "#c = :c AND #b > :b", names = {
				@ExpressionName(name = "#c", value = "category"), @ExpressionName(name = "#b", value = "brand") })
		List<Doc> brandAfter(float[] queryVector, @Param("c") String category, @Param("b") String brand);

		@VectorQuery(topK = 500)
		List<Doc> beyondTopKRange(float[] queryVector, @Filter("category") String category);

		@VectorQuery(topK = 3)
		List<Doc> findNearest(float[] queryVector);
	}

	@EnableDynamoDbRepositories(basePackageClasses = VectorSearchIntegrationTest.class, considerNestedRepositories = true, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.awspring\\.spring\\.data\\.dynamodb\\.integration\\.(?!VectorSearchIntegrationTest\\$).*"))
	static class TestConfig extends AbstractDynamoDbConfiguration {

		private final DynamoDbClient dynamoDbClient;

		TestConfig(DynamoDbClient dynamoDbClient) {
			this.dynamoDbClient = dynamoDbClient;
		}

		@Bean
		@Override
		public DynamoDbClient dynamoDbClient() {
			return dynamoDbClient;
		}
	}

	@BeforeEach
	void setUp() {
		DynamoDbClient dynamoDbClient = DynamoDbClient.builder().region(Region.of(floci.getRegion()))
				.endpointOverride(URI.create(floci.getEndpoint())).credentialsProvider(StaticCredentialsProvider
						.create(AwsBasicCredentials.create(floci.getAccessKey(), floci.getSecretKey())))
				.build();

		recreateTable(dynamoDbClient);

		context = new AnnotationConfigApplicationContext();
		context.registerBean(DynamoDbClient.class, () -> dynamoDbClient);
		context.register(TestConfig.class);
		context.refresh();
		docs = context.getBean(DocRepository.class);

		docs.saveAll(List.of(new Doc("d1", "audio", "acme", 1f, 0f, 0f), new Doc("d2", "audio", "zen", 0.9f, 0.1f, 0f),
				new Doc("d3", "audio", "acme", 0f, 1f, 0f), new Doc("d4", "video", "acme", 1f, 0f, 0f)));
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.close();
		}
	}

	private static void recreateTable(DynamoDbClient client) {
		try {
			client.deleteTable(builder -> builder.tableName(TABLE_NAME));
		}
		catch (ResourceNotFoundException notFound) {
		}
		client.createTable(builder -> builder.tableName(TABLE_NAME).billingMode(BillingMode.PAY_PER_REQUEST)
				.attributeDefinitions(
						AttributeDefinition.builder().attributeName("docId").attributeType(ScalarAttributeType.S)
								.build(),
						AttributeDefinition.builder().attributeName("category").attributeType(ScalarAttributeType.S)
								.build(),
						AttributeDefinition.builder().attributeName("brand").attributeType(ScalarAttributeType.S)
								.build())
				.keySchema(KeySchemaElement.builder().attributeName("docId").keyType(KeyType.HASH).build())
				.vectorIndexes(index -> index.indexName(INDEX_NAME)
						.vectorAttribute(attribute -> attribute.attributeName("embedding")).dimensions(3L)
						.distanceFunction(VectorDistanceFunction.COSINE)
						.projection(projection -> projection.projectionType(ProjectionType.ALL)).searchSchema(
								element -> element.attributeName("category")
										.searchSchemaElementType(SearchSchemaElementType.HASH),
								element -> element.attributeName("brand")
										.searchSchemaElementType(SearchSchemaElementType.INLINE_FILTER))));
	}

	@Test
	@DisplayName("@Filter on the HASH key scopes the search; results come back most similar first with scores")
	void vectorQuery_scopedByHashKey_orderedByScore() {
		List<Doc> found = docs.findNearestInCategory(QUERY_VECTOR, "audio");

		assertAll(() -> assertEquals(List.of("d1", "d2", "d3"), found.stream().map(Doc::getDocId).toList()),
				() -> assertEquals(0.0, found.get(0).getScore()),
				() -> assertTrue(found.get(1).getScore() < found.get(2).getScore()),
				() -> assertNull(found.get(0).getEmbedding()));
	}

	@Test
	@DisplayName("@VectorSearch combines the HASH key with an INLINE_FILTER equality condition")
	void vectorSearch_hashKeyAndInlineFilter() {
		List<Doc> found = docs.inCategoryAndBrand(QUERY_VECTOR, "audio", "acme");

		assertEquals(List.of("d1", "d3"), found.stream().map(Doc::getDocId).toList());
	}

	@Test
	@DisplayName("A search without the HASH key condition is rejected by DynamoDB, not by the library")
	void searchWithoutHashKey_rejectedByService() {
		UncategorizedDynamoDbException exception = assertThrows(UncategorizedDynamoDbException.class,
				() -> docs.findNearest(QUERY_VECTOR));

		assertTrue(exception.getMessage().contains("SearchConditionExpression must be provided"));
	}

	@Test
	@DisplayName("A non-equality comparator is rejected by DynamoDB, not by the library")
	void nonEqualityComparator_rejectedByService() {
		UncategorizedDynamoDbException exception = assertThrows(UncategorizedDynamoDbException.class,
				() -> docs.brandAfter(QUERY_VECTOR, "audio", "a"));

		assertTrue(exception.getMessage().contains("Invalid comparator used in SearchConditionExpression"));
	}

	@Test
	@DisplayName("A topK outside the service range is rejected by DynamoDB, not by the library")
	void topKOutOfRange_rejectedByService() {
		UncategorizedDynamoDbException exception = assertThrows(UncategorizedDynamoDbException.class,
				() -> docs.beyondTopKRange(QUERY_VECTOR, "audio"));

		assertTrue(exception.getMessage().contains("out of valid range"));
	}
}
