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
package io.awspring.cloud.dynamodb.integration;

import io.awspring.cloud.dynamodb.LocalStackTestContainer;
import io.awspring.cloud.dynamodb.config.AbstractDynamoDbConfiguration;
import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.repository.DynamoDbRepository;
import io.awspring.cloud.dynamodb.repository.config.EnableDynamoDbRepositories;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

public class SortKeyTemplateIntegrationTest extends LocalStackTestContainer {

	private static final String TABLE_NAME = "orders_sk_template";

	private DynamoDbClient dynamoDbClient;
	private MappingDynamoDbConverter converter;
	private AnnotationConfigApplicationContext context;
	private MatchRepository repository;

	@Table(tableName = TABLE_NAME)
	@SortKeyTemplate("MATCH#{year}#{round}")
	public static class Match {

		@PartitionKey
		private String tournamentId;
		private int year;
		private String round;

		public Match() {
		}

		public Match(String tournamentId, int year, String round) {
			this.tournamentId = tournamentId;
			this.year = year;
			this.round = round;
		}

		public String getTournamentId() {
			return tournamentId;
		}

		public void setTournamentId(String tournamentId) {
			this.tournamentId = tournamentId;
		}

		public int getYear() {
			return year;
		}

		public void setYear(int year) {
			this.year = year;
		}

		public String getRound() {
			return round;
		}

		public void setRound(String round) {
			this.round = round;
		}
	}

	public interface MatchRepository extends DynamoDbRepository<Match, String> {

		List<Match> findByTournamentIdAndYear(String tournamentId, int year);

		List<Match> findByTournamentIdAndYearAndRound(String tournamentId, int year, String round);
	}

	@EnableDynamoDbRepositories(basePackageClasses = SortKeyTemplateIntegrationTest.class, considerNestedRepositories = true, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.REGEX, pattern = "io\\.awspring\\.cloud\\.dynamodb\\.integration\\.(?!SortKeyTemplateIntegrationTest\\$).*"))
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
		dynamoDbClient = DynamoDbClient.builder().region(Region.of(localstack.getRegion()))
				.endpointOverride(localstack.getEndpoint())
				.credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
						.create(software.amazon.awssdk.auth.credentials.AwsBasicCredentials
								.create(localstack.getAccessKey(), localstack.getSecretKey())))
				.build();

		try {
			dynamoDbClient.deleteTable(builder -> builder.tableName(TABLE_NAME));
		}
		catch (ResourceNotFoundException notFound) {
		}

		dynamoDbClient.createTable(CreateTableRequest.builder().tableName(TABLE_NAME)
				.attributeDefinitions(
						AttributeDefinition.builder().attributeName("tournamentId").attributeType(ScalarAttributeType.S)
								.build(),
						AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
				.keySchema(KeySchemaElement.builder().attributeName("tournamentId").keyType(KeyType.HASH).build(),
						KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
				.provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
				.build());

		converter = new MappingDynamoDbConverter(new DynamoDbMappingContext());
		converter.afterPropertiesSet();

		context = new AnnotationConfigApplicationContext();
		context.registerBean(DynamoDbClient.class, () -> dynamoDbClient);
		context.register(TestConfig.class);
		context.refresh();
		repository = context.getBean(MatchRepository.class);
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.close();
		}
	}

	private Map<String, AttributeValue> rawGetItem(String tournamentId, String sk) {
		Map<String, AttributeValue> key = new HashMap<>();
		key.put("tournamentId", AttributeValue.builder().s(tournamentId).build());
		key.put("sk", AttributeValue.builder().s(sk).build());
		return dynamoDbClient
				.getItem(GetItemRequest.builder().tableName(TABLE_NAME).key(key).consistentRead(true).build()).item();
	}

	@Test
	void saveWritesTheComposedSortKeyAsTheRawSkAttribute() {
		repository.save(new Match("cust-1", 2024, "QUARTERFINAL"));

		Map<String, AttributeValue> stored = rawGetItem("cust-1", "MATCH#2024#QUARTERFINAL");

		Assertions.assertFalse(stored.isEmpty(), "item must be retrievable by its composed sort key");
		Assertions.assertNotNull(stored.get("sk"), "the @SortKeyTemplate must materialise the 'sk' attribute");
		Assertions.assertEquals("MATCH#2024#QUARTERFINAL", stored.get("sk").s());
		Assertions.assertEquals("2024", stored.get("year").n());
		Assertions.assertEquals("QUARTERFINAL", stored.get("round").s());
	}

	@Test
	void readDecomposesTheStoredSortKeyBackOntoPlaceholderProperties() {
		Map<String, AttributeValue> raw = new HashMap<>();
		raw.put("tournamentId", AttributeValue.builder().s("cust-decompose").build());
		raw.put("sk", AttributeValue.builder().s("MATCH#2020#PENDING").build());
		raw.put("year", AttributeValue.builder().n("1999").build());
		raw.put("round", AttributeValue.builder().s("WRONG").build());
		dynamoDbClient.putItem(PutItemRequest.builder().tableName(TABLE_NAME).item(raw).build());

		Map<String, AttributeValue> stored = rawGetItem("cust-decompose", "MATCH#2020#PENDING");
		Match readBack = converter.read(Match.class, stored);

		Assertions.assertEquals("cust-decompose", readBack.getTournamentId());
		Assertions.assertEquals(2020, readBack.getYear(), "year must be reconstructed from the composed sort key");
		Assertions.assertEquals("PENDING", readBack.getRound(),
				"round must be reconstructed from the composed sort key");
	}

	@Test
	void derivedBeginsWithFinderReturnsOnlyTheMatchingYearForThatCustomer() {
		repository.save(new Match("cust-1", 2022, "NEW"));
		repository.save(new Match("cust-1", 2023, "QUARTERFINAL"));
		repository.save(new Match("cust-1", 2023, "PENDING"));
		repository.save(new Match("cust-1", 2024, "QUARTERFINAL"));
		repository.save(new Match("cust-2", 2023, "QUARTERFINAL"));

		List<Match> found = repository.findByTournamentIdAndYear("cust-1", 2023);

		Assertions.assertEquals(2, found.size(),
				"begins_with(sk, \"MATCH#2023#\") must return exactly cust-1's two 2023 orders");
		Assertions.assertTrue(found.stream().allMatch(o -> "cust-1".equals(o.getTournamentId())),
				"partition-key condition must exclude the other customer");
		Assertions.assertTrue(found.stream().allMatch(o -> o.getYear() == 2023),
				"begins_with prefix must exclude the 2022 and 2024 orders");
	}

	@Test
	void derivedFullyBoundFinderMatchesExactlyTheComposedSortKey() {
		repository.save(new Match("cust-1", 2023, "QUARTERFINAL"));
		repository.save(new Match("cust-1", 2023, "PENDING"));

		List<Match> found = repository.findByTournamentIdAndYearAndRound("cust-1", 2023, "PENDING");

		Assertions.assertEquals(1, found.size(),
				"EQ on the fully-composed \"MATCH#2023#PENDING\" must match exactly one match");
		Assertions.assertEquals(2023, found.get(0).getYear());
		Assertions.assertEquals("PENDING", found.get(0).getRound());
	}
}
