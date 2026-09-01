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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.LocalStackTestContainer;
import io.awspring.spring.data.dynamodb.config.AbstractDynamoDbConfiguration;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.repository.DynamoDbCompositeId;
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepository;
import io.awspring.spring.data.dynamodb.repository.ExpressionName;
import io.awspring.spring.data.dynamodb.repository.Update;
import io.awspring.spring.data.dynamodb.repository.config.EnableDynamoDbRepositories;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.query.Param;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

public class DerivedQueryBehaviourIntegrationTest extends LocalStackTestContainer {

	private static final String TABLE_NAME = "derived_query_behaviour_it";
	private static final String SENSOR_A = "A";
	private static final String SENSOR_B = "B";
	private static final String SENSOR_MISSING = "missing";
	private static final String REGION_EU = "eu";
	private static final String REGION_US = "us";

	private AnnotationConfigApplicationContext context;
	private ReadingRepository repository;

	@Table(tableName = TABLE_NAME)
	public static class Reading {

		@PartitionKey
		private String sensor;

		@SortKey
		private Long minute;

		private String label;

		private String region;

		public Reading() {
		}

		public Reading(String sensor, Long minute, String label, String region) {
			this.sensor = sensor;
			this.minute = minute;
			this.label = label;
			this.region = region;
		}

		public String getSensor() {
			return sensor;
		}

		public void setSensor(String sensor) {
			this.sensor = sensor;
		}

		public Long getMinute() {
			return minute;
		}

		public void setMinute(Long minute) {
			this.minute = minute;
		}

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		public String getRegion() {
			return region;
		}

		public void setRegion(String region) {
			this.region = region;
		}
	}

	public interface ReadingRepository extends DynamoDbRepository<Reading, DynamoDbCompositeId> {

		List<Reading> findBySensor(String sensor);

		List<Reading> findBySensorAndMinuteBetween(String sensor, Long from, Long to);

		List<Reading> findBySensorAndMinuteGreaterThan(String sensor, Long minute);

		List<Reading> findBySensorAndMinuteGreaterThanEqual(String sensor, Long minute);

		List<Reading> findBySensorAndMinuteLessThan(String sensor, Long minute);

		List<Reading> findBySensorAndMinuteLessThanEqual(String sensor, Long minute);

		List<Reading> findBySensorAndMinuteGreaterThan(String sensor, Long minute, Limit limit);

		List<Reading> findBySensorAndRegion(String sensor, String region);

		List<Reading> findBySensorAndLabelIn(String sensor, List<String> labels);

		List<Reading> findBySensorAndLabelNotIn(String sensor, List<String> labels);

		List<Reading> findBySensorAndLabelStartingWith(String sensor, String prefix);

		List<Reading> findBySensorAndLabelContaining(String sensor, String fragment);

		List<Reading> findBySensorAndLabelBetween(String sensor, String from, String to);

		long countBySensor(String sensor);

		long countBySensorAndMinuteBetween(String sensor, Long from, Long to);

		boolean existsBySensor(String sensor);

		boolean existsBySensorAndMinuteGreaterThan(String sensor, Long minute);

		@Update(updateExpression = "SET #label = :label", names = @ExpressionName(name = "#label", value = "label"))
		Reading changeLabel(@Param("sensor") String sensor, @Param("minute") Long minute, @Param("label") String label);

		@Update(updateExpression = "SET #label = :label", conditionExpression = "#region = :expectedRegion", names = {
				@ExpressionName(name = "#label", value = "label"),
				@ExpressionName(name = "#region", value = "region") })
		Reading changeLabelWhenRegion(@Param("sensor") String sensor, @Param("minute") Long minute,
				@Param("label") String label, @Param("expectedRegion") String expectedRegion);
	}

	@EnableDynamoDbRepositories(basePackageClasses = DerivedQueryBehaviourIntegrationTest.class, considerNestedRepositories = true, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.awspring\\.spring\\.data\\.dynamodb\\.integration\\.(?!DerivedQueryBehaviourIntegrationTest\\$).*"))
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
		DynamoDbClient client = DynamoDbClient.builder().region(Region.of(localstack.getRegion()))
				.endpointOverride(localstack.getEndpoint())
				.credentialsProvider(StaticCredentialsProvider
						.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
				.build();

		recreateTable(client);

		context = new AnnotationConfigApplicationContext();
		context.registerBean(DynamoDbClient.class, () -> client);
		context.register(TestConfig.class);
		context.refresh();
		repository = context.getBean(ReadingRepository.class);

		seed();
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
		client.createTable(CreateTableRequest.builder().tableName(TABLE_NAME).attributeDefinitions(
				AttributeDefinition.builder().attributeName("sensor").attributeType(ScalarAttributeType.S).build(),
				AttributeDefinition.builder().attributeName("minute").attributeType(ScalarAttributeType.N).build())
				.keySchema(KeySchemaElement.builder().attributeName("sensor").keyType(KeyType.HASH).build(),
						KeySchemaElement.builder().attributeName("minute").keyType(KeyType.RANGE).build())
				.provisionedThroughput(
						ProvisionedThroughput.builder().readCapacityUnits(20L).writeCapacityUnits(20L).build())
				.build());
	}

	private void seed() {
		for (long minute = 1; minute <= 10; minute++) {
			repository.save(new Reading(SENSOR_A, minute, "reading-" + minute, minute <= 5 ? REGION_EU : REGION_US));
		}
		for (long minute = 1; minute <= 4; minute++) {
			repository.save(new Reading(SENSOR_B, minute, "other-" + minute, REGION_EU));
		}
	}

	private static List<Long> minutesOf(List<Reading> readings) {
		List<Long> minutes = new ArrayList<>();
		readings.forEach(reading -> minutes.add(reading.getMinute()));
		return minutes;
	}

	@Nested
	@DisplayName("Sort-key ranges (KeyConditionExpression)")
	class SortKeyRanges {

		@Test
		@DisplayName("BETWEEN returns the inclusive range and nothing outside it")
		void between_inclusiveRange_returnsExactly() {
			List<Reading> found = repository.findBySensorAndMinuteBetween(SENSOR_A, 3L, 6L);

			assertEquals(List.of(3L, 4L, 5L, 6L), minutesOf(found),
					"BETWEEN 3 AND 6 must return exactly minutes 3,4,5,6 -- if the lower bound were lost the range "
							+ "would collapse to an equality check on the upper bound and return only minute 6");
		}

		@Test
		@DisplayName("BETWEEN with equal bounds returns just that row")
		void between_equalBounds_returnsSingleRow() {
			List<Reading> found = repository.findBySensorAndMinuteBetween(SENSOR_A, 4L, 4L);

			assertEquals(List.of(4L), minutesOf(found), "BETWEEN 4 AND 4 is a legitimate single-row range");
		}

		@Test
		@DisplayName("BETWEEN spanning the whole partition returns every row")
		void between_fullRange_returnsAll() {
			List<Reading> found = repository.findBySensorAndMinuteBetween(SENSOR_A, 1L, 10L);

			assertAll("full range query",
					() -> assertEquals(10, found.size(), "the full range must return all ten rows of sensor A"),
					() -> assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), minutesOf(found)));
		}

		@Test
		@DisplayName("BETWEEN never leaks rows from another partition")
		void between_otherPartition_staysIsolated() {
			List<Reading> found = repository.findBySensorAndMinuteBetween(SENSOR_B, 1L, 10L);

			assertAll("partition isolation",
					() -> assertEquals(List.of(1L, 2L, 3L, 4L), minutesOf(found), "sensor B has only four rows"),
					() -> found.forEach(
							reading -> assertEquals(SENSOR_B, reading.getSensor(), "no row from sensor A may appear")));
		}

		@Test
		@DisplayName("each comparison operator returns its exact half-open range")
		void comparisonOperators_exactRanges() {
			assertAll("comparison operators",
					() -> assertEquals(List.of(8L, 9L, 10L),
							minutesOf(repository.findBySensorAndMinuteGreaterThan(SENSOR_A, 7L)), "> 7 excludes 7"),
					() -> assertEquals(List.of(7L, 8L, 9L, 10L),
							minutesOf(repository.findBySensorAndMinuteGreaterThanEqual(SENSOR_A, 7L)),
							">= 7 includes 7"),
					() -> assertEquals(List.of(1L, 2L),
							minutesOf(repository.findBySensorAndMinuteLessThan(SENSOR_A, 3L)), "< 3 excludes 3"),
					() -> assertEquals(List.of(1L, 2L, 3L),
							minutesOf(repository.findBySensorAndMinuteLessThanEqual(SENSOR_A, 3L)), "<= 3 includes 3"));
		}

		@Test
		@DisplayName("a sort-key range is a key condition, provable through Limit")
		void sortKeyRange_isKeyCondition_notFilter() {
			List<Reading> found = repository.findBySensorAndMinuteGreaterThan(SENSOR_A, 5L, Limit.of(3));

			assertEquals(List.of(6L, 7L, 8L), minutesOf(found),
					"DynamoDB applies Limit before a FilterExpression but after a KeyConditionExpression. Getting "
							+ "an empty result here means the sort-key range degraded into a filter and the Limit "
							+ "consumed rows 1-3 before the predicate ever ran.");
		}
	}

	@Nested
	@DisplayName("Non-key predicates (FilterExpression)")
	class NonKeyPredicates {

		@Test
		@DisplayName("a non-key equality filters within the partition")
		void equality_nonKeyAttribute_filtersCorrectly() {
			List<Reading> found = repository.findBySensorAndRegion(SENSOR_A, REGION_EU);

			assertEquals(List.of(1L, 2L, 3L, 4L, 5L), minutesOf(found), "only sensor A's eu rows");
		}

		@Test
		@DisplayName("IN returns exactly the listed values")
		void in_matchesExactly() {
			List<Reading> found = repository.findBySensorAndLabelIn(SENSOR_A, List.of("reading-2", "reading-7"));

			assertEquals(List.of(2L, 7L), minutesOf(found), "IN must match every listed element and nothing else");
		}

		@Test
		@DisplayName("an empty IN list matches nothing instead of failing or matching everything")
		void in_emptyList_matchesNothing() {
			List<Reading> found = repository.findBySensorAndLabelIn(SENSOR_A, List.of());

			assertTrue(found.isEmpty(),
					"'x IN ()' is invalid DynamoDB syntax; collapsing it to a match-nothing constant is the only "
							+ "safe reading of an empty candidate list -- returning the whole partition would be worse");
		}

		@Test
		@DisplayName("NOT IN excludes exactly the listed values")
		void notIn_excludesExactly() {
			List<Reading> found = repository.findBySensorAndLabelNotIn(SENSOR_A, List.of("reading-1", "reading-2"));

			assertEquals(List.of(3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), minutesOf(found),
					"NOT IN must exclude only the two");
		}

		@Test
		@DisplayName("an empty NOT IN list matches everything")
		void notIn_emptyList_matchesAll() {
			List<Reading> found = repository.findBySensorAndLabelNotIn(SENSOR_A, List.of());

			assertEquals(10, found.size(), "excluding nothing must leave the whole partition intact");
		}

		@Test
		@DisplayName("StartingWith and Containing translate to the right functions")
		void prefixAndSubstring_correctSemantics() {
			assertAll("prefix and substring predicates",
					() -> assertEquals(10, repository.findBySensorAndLabelStartingWith(SENSOR_A, "reading-").size(),
							"every label in sensor A starts with 'reading-'"),
					() -> assertTrue(repository.findBySensorAndLabelStartingWith(SENSOR_A, "other-").isEmpty(),
							"begins_with must not match a substring that appears later in the value"),
					() -> assertEquals(List.of(10L),
							minutesOf(repository.findBySensorAndLabelContaining(SENSOR_A, "g-10")),
							"contains must match an interior fragment"));
		}

		@Test
		@DisplayName("a non-key BETWEEN keeps both bounds in the filter expression")
		void between_nonKey_keepsBothBounds() {
			List<Reading> found = repository.findBySensorAndLabelBetween(SENSOR_A, "reading-2", "reading-4");

			assertEquals(List.of(2L, 3L, 4L), minutesOf(found),
					"a filter-expression BETWEEN must keep both bounds in separate value slots; sharing one slot "
							+ "collapses the range to an equality check on the upper bound and would return only minute 4");
		}
	}

	@Nested
	@DisplayName("Update queries")
	class UpdateQueries {

		@Test
		@DisplayName("@Update update persists and returns the updated entity")
		void updateQuery_updateExpression_persistsChange() {
			Reading updated = repository.changeLabel(SENSOR_A, 1L, "updated-label");
			Reading persisted = repository.findBySensorAndMinuteBetween(SENSOR_A, 1L, 1L).get(0);

			assertAll("update expression", () -> assertEquals("updated-label", updated.getLabel()),
					() -> assertEquals("updated-label", persisted.getLabel()),
					() -> assertEquals(SENSOR_A, persisted.getSensor()), () -> assertEquals(1L, persisted.getMinute()));
		}

		@Test
		@DisplayName("conditionExpression applies an update only when the condition matches")
		void updateQuery_conditionExpression_guardsUpdate() {
			Reading updated = repository.changeLabelWhenRegion(SENSOR_A, 1L, "conditional-label", REGION_EU);

			assertEquals("conditional-label", updated.getLabel());
			assertThrows(ConcurrencyFailureException.class,
					() -> repository.changeLabelWhenRegion(SENSOR_A, 1L, "rejected-label", REGION_US));

			Reading persisted = repository.findBySensorAndMinuteBetween(SENSOR_A, 1L, 1L).get(0);
			assertEquals("conditional-label", persisted.getLabel(),
					"a failed condition must leave the previously accepted value unchanged");
		}
	}

	@Nested
	@DisplayName("Count and exists")
	class CountAndExists {

		@Test
		@DisplayName("count respects the partition key instead of counting the table")
		void count_scopedToPartition() {
			assertAll("count by partition",
					() -> assertEquals(10L, repository.countBySensor(SENSOR_A),
							"sensor A has ten rows; getting 14 means the key condition was dropped"),
					() -> assertEquals(4L, repository.countBySensor(SENSOR_B), "sensor B has four rows"),
					() -> assertEquals(0L, repository.countBySensor(SENSOR_MISSING),
							"an absent partition counts zero"));
		}

		@Test
		@DisplayName("count respects a sort-key range too")
		void count_scopedToSortKeyRange() {
			assertAll("count by sort-key range",
					() -> assertEquals(4L, repository.countBySensorAndMinuteBetween(SENSOR_A, 3L, 6L),
							"minutes 3,4,5,6 -- a lost lower bound would count only minute 6"),
					() -> assertEquals(1L, repository.countBySensorAndMinuteBetween(SENSOR_A, 10L, 10L),
							"a single-row range counts one"));
		}

		@Test
		@DisplayName("exists respects the partition key")
		void exists_scopedToPartition() {
			assertAll("exists by partition", () -> assertTrue(repository.existsBySensor(SENSOR_A), "sensor A has rows"),
					() -> assertTrue(repository.existsBySensor(SENSOR_B), "sensor B has rows"),
					() -> assertFalse(repository.existsBySensor(SENSOR_MISSING),
							"an absent partition must report false; returning true means the query ignored the key"));
		}

		@Test
		@DisplayName("exists respects a sort-key range")
		void exists_scopedToSortKeyRange() {
			assertAll("exists by sort-key range",
					() -> assertTrue(repository.existsBySensorAndMinuteGreaterThan(SENSOR_A, 9L),
							"minute 10 is greater than 9"),
					() -> assertFalse(repository.existsBySensorAndMinuteGreaterThan(SENSOR_A, 10L),
							"nothing is greater than minute 10"),
					() -> assertFalse(repository.existsBySensorAndMinuteGreaterThan(SENSOR_B, 4L),
							"sensor B stops at minute 4"));
		}
	}

	@Nested
	@DisplayName("Partition queries")
	class PartitionQueries {

		@Test
		@DisplayName("a plain partition query returns the whole partition and only it")
		void findBySensor_returnsOnlyThatPartition() {
			List<Reading> found = repository.findBySensor(SENSOR_A);

			assertAll("partition isolation", () -> assertEquals(10, found.size(), "sensor A has ten rows"), () -> found
					.forEach(reading -> assertEquals(SENSOR_A, reading.getSensor(), "no other partition may leak in")));
		}
	}
}
