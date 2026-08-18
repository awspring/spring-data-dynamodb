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
package io.awspring.cloud.dynamodb.mapping;

import static org.junit.jupiter.api.Assertions.*;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class SortKeyTemplateConverterTest {

	private static final String TOURNAMENT_ID = "cust-1";
	private static final int YEAR_2024 = 2024;
	private static final int YEAR_2025 = 2025;
	private static final String ROUND_QUARTERFINAL = "QUARTERFINAL";
	private static final String COMPOSED_MATCH_SK = "MATCH#2024#QUARTERFINAL";
	private static final String COMPOSED_OVERLOADED_SK = "ROUND#QUARTERFINAL#2024";

	@Table(tableName = "orders")
	@SortKeyTemplate("MATCH#{year}#{round}")
	static class Match {
		@PartitionKey
		String tournamentId;
		int year;
		String round;

		Match() {
		}

		Match(String tournamentId, int year, String round) {
			this.tournamentId = tournamentId;
			this.year = year;
			this.round = round;
		}
	}

	@Table(tableName = "events")
	@SortKeyTemplate("EVENT#{category}#{name}")
	static class Event {
		@PartitionKey
		String pk;
		String category;
		String name;
	}

	@Table(tableName = "orders_overloaded_column")
	@SortKeyTemplate(value = "ROUND#{round}#{year}", column = "gsi1sk")
	static class MatchWithOverloadedColumnTemplate {
		@PartitionKey
		String tournamentId;
		int year;
		String round;

		MatchWithOverloadedColumnTemplate() {
		}

		MatchWithOverloadedColumnTemplate(String tournamentId, int year, String round) {
			this.tournamentId = tournamentId;
			this.year = year;
			this.round = round;
		}
	}

	@Table(tableName = "conflict")
	@SortKeyTemplate("X#{foo}")
	static class ConflictEntity {
		@PartitionKey
		String pk;
		@SortKey
		String sk;
		String foo;
	}

	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		converter = new MappingDynamoDbConverter(new DynamoDbMappingContext());
		converter.afterPropertiesSet();
	}

	@Nested
	@DisplayName("Write compose")
	class WriteCompose {

		@Test
		@DisplayName("Write composes the template into the configured base-table column")
		void write_composesTemplate_intoBaseTableColumn() {
			Match match = new Match(TOURNAMENT_ID, YEAR_2024, ROUND_QUARTERFINAL);

			Map<String, AttributeValue> item = new HashMap<>();
			converter.write(match, item);

			assertAll("composed write",
					() -> assertNotNull(item.get("sk"), "template must materialise the base-table 'sk' column"),
					() -> assertEquals(COMPOSED_MATCH_SK, item.get("sk").s()),
					() -> assertEquals("2024", item.get("year").n()),
					() -> assertEquals(ROUND_QUARTERFINAL, item.get("round").s()));
		}
	}

	@Nested
	@DisplayName("Read decompose")
	class ReadDecompose {

		@Test
		@DisplayName("Read reconstructs placeholder properties from the composed column")
		void read_reconstructsPlaceholderProperties() {
			Map<String, AttributeValue> source = new HashMap<>();
			source.put("pk", AttributeValue.builder().s("P").build());
			source.put("sk", AttributeValue.builder().s("EVENT#music#concert").build());

			Event readBack = converter.read(Event.class, source);

			assertAll("decomposed read", () -> assertEquals("P", readBack.pk),
					() -> assertEquals("music", readBack.category), () -> assertEquals("concert", readBack.name));
		}

		@Test
		@DisplayName("Reading an item whose sort key does not match the template skips decomposition")
		void read_mismatchedSortKey_skipsDecomposition() {
			Map<String, AttributeValue> foreignItem = new HashMap<>();
			foreignItem.put("tournamentId", AttributeValue.fromS("t1"));
			foreignItem.put("year", AttributeValue.fromN("2024"));
			foreignItem.put("sk", AttributeValue.fromS("CUSTOMER#c1"));

			Match result = converter.read(Match.class, foreignItem);

			assertAll("graceful skip", () -> assertNotNull(result), () -> assertEquals("t1", result.tournamentId),
					() -> assertEquals(YEAR_2024, result.year), () -> assertNull(result.round));
		}
	}

	@Nested
	@DisplayName("Round-trip")
	class RoundTrip {

		@Test
		@DisplayName("Write then read round-trips through the converter")
		void writeThenRead_roundTrips() {
			Match match = new Match(TOURNAMENT_ID, YEAR_2025, "PENDING");

			Map<String, AttributeValue> item = new HashMap<>();
			converter.write(match, item);

			Match readBack = converter.read(Match.class, item);

			assertAll("round-tripped values", () -> assertEquals(match.tournamentId, readBack.tournamentId),
					() -> assertEquals(match.year, readBack.year), () -> assertEquals(match.round, readBack.round));
		}
	}

	@Nested
	@DisplayName("Conflicts")
	class Conflicts {

		@Test
		@DisplayName("Declaring both @SortKey and @SortKeyTemplate on the same index throws")
		void sortKeyAndTemplate_sameIndex_throws() {
			ConflictEntity entity = new ConflictEntity();
			entity.pk = "p";
			entity.sk = "s";
			entity.foo = "bar";

			Throwable ex = assertThrows(Throwable.class, () -> converter.write(entity, new HashMap<>()));

			assertTrue(allMessages(ex).contains("@SortKeyTemplate"),
					"exception should explain the @SortKey/@SortKeyTemplate conflict; was: " + allMessages(ex));
		}
	}

	@Nested
	@DisplayName("Overloaded column")
	class OverloadedColumn {

		@Test
		@DisplayName("Template with explicit column materialises that overloaded column instead of sk")
		void explicitColumn_materialisesOverloadedColumn() {
			MatchWithOverloadedColumnTemplate match = new MatchWithOverloadedColumnTemplate(TOURNAMENT_ID, YEAR_2024,
					ROUND_QUARTERFINAL);

			Map<String, AttributeValue> item = new HashMap<>();
			converter.write(match, item);

			assertAll("overloaded column",
					() -> assertNotNull(item.get("gsi1sk"),
							"template must materialise its configured overloaded column"),
					() -> assertEquals(COMPOSED_OVERLOADED_SK, item.get("gsi1sk").s()));
		}
	}

	private static String allMessages(Throwable throwable) {
		StringBuilder builder = new StringBuilder();
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current.getMessage() != null) {
				builder.append(current.getMessage()).append('\n');
			}
			for (Throwable suppressed : current.getSuppressed()) {
				if (suppressed.getMessage() != null) {
					builder.append(suppressed.getMessage()).append('\n');
				}
			}
		}
		return builder.toString();
	}
}
