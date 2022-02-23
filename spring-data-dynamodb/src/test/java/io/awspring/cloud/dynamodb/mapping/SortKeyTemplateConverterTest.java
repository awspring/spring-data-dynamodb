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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class SortKeyTemplateConverterTest {

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

	@Test
	void writeComposesTheTemplateIntoTheConfiguredBaseTableColumn() {
		Match match = new Match("cust-1", 2024, "QUARTERFINAL");

		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(match, item);

		assertNotNull(item.get("sk"), "template must materialise the base-table 'sk' column");
		assertEquals("MATCH#2024#QUARTERFINAL", item.get("sk").s());
		assertEquals("2024", item.get("year").n());
		assertEquals("QUARTERFINAL", item.get("round").s());
	}

	@Test
	void readReconstructsPlaceholderPropertiesFromTheComposedColumn() {
		Map<String, AttributeValue> source = new HashMap<>();
		source.put("pk", AttributeValue.builder().s("P").build());
		source.put("sk", AttributeValue.builder().s("EVENT#music#concert").build());

		Event readBack = converter.read(Event.class, source);

		assertEquals("P", readBack.pk);
		assertEquals("music", readBack.category);
		assertEquals("concert", readBack.name);
	}

	@Test
	void writeThenReadRoundTripsThroughTheConverter() {
		Match match = new Match("cust-1", 2025, "PENDING");

		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(match, item);
		Match readBack = converter.read(Match.class, item);

		assertEquals(match.tournamentId, readBack.tournamentId);
		assertEquals(match.year, readBack.year);
		assertEquals(match.round, readBack.round);
	}

	@Test
	void declaringBothSortKeyAndSortKeyTemplateOnTheSameIndexThrowsAtWriteTime() {
		ConflictEntity entity = new ConflictEntity();
		entity.pk = "p";
		entity.sk = "s";
		entity.foo = "bar";

		Throwable ex = assertThrows(Throwable.class, () -> converter.write(entity, new HashMap<>()));
		assertTrue(allMessages(ex).contains("@SortKeyTemplate"),
				"exception should explain the @SortKey/@SortKeyTemplate conflict; was: " + allMessages(ex));
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

	@Test
	void templateWithAnExplicitColumnMaterialisesThatOverloadedColumnInsteadOfSk() {
		MatchWithOverloadedColumnTemplate match = new MatchWithOverloadedColumnTemplate("cust-1", 2024, "QUARTERFINAL");

		Map<String, AttributeValue> item = new HashMap<>();
		converter.write(match, item);

		assertNotNull(item.get("gsi1sk"), "template must materialise its configured overloaded column");
		assertEquals("ROUND#QUARTERFINAL#2024", item.get("gsi1sk").s());
	}
}
