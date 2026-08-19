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
package io.awspring.spring.data.dynamodb.mapping;

import static org.junit.jupiter.api.Assertions.*;

import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.spring.data.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.spring.data.dynamodb.core.mapping.KeyTemplateResolver;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.mapping.MappingException;

class KeyTemplateResolverTest {

	private static final String COLUMN_SK = "sk";
	private static final String COLUMN_GSI1SK = "gsi1sk";
	private static final String TOURNAMENT_ID = "cust-1";
	private static final int YEAR_2024 = 2024;
	private static final int YEAR_2025 = 2025;
	private static final String ROUND_QUARTERFINAL = "QUARTERFINAL";
	private static final String COMPOSED_SK = "MATCH#2024#QUARTERFINAL";

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

	@Table(tableName = "orders_overloaded_column")
	@SortKeyTemplate(value = "ROUND#{round}#{year}", column = "gsi1sk")
	static class MatchWithOverloadedColumnTemplate {
		@PartitionKey
		String tournamentId;
		int year;
		String round;
	}

	@Table(tableName = "plain")
	static class PlainEntity {
		@PartitionKey
		String pk;
	}

	private DynamoDbPersistentEntity<?> entityFor(Class<?> type) {
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		return mappingContext.getRequiredPersistentEntity(type);
	}

	@Nested
	@DisplayName("Compose")
	class Compose {

		@Test
		@DisplayName("Composes the base-table sort key from bound properties")
		void compose_boundProperties_producesExpectedKey() {
			DynamoDbPersistentEntity<?> entity = entityFor(Match.class);
			KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

			Match match = new Match(TOURNAMENT_ID, YEAR_2024, ROUND_QUARTERFINAL);

			String sortKey = resolver.compose(COLUMN_SK, match, new DefaultConversionService());

			assertAll("composed sort key", () -> assertEquals(COMPOSED_SK, sortKey),
					() -> assertEquals(COLUMN_SK, resolver.columnFor(COLUMN_SK)));
		}
	}

	@Nested
	@DisplayName("Decompose")
	class Decompose {

		@Test
		@DisplayName("Decomposes the physical string back onto the instance's properties")
		void decompose_physicalString_setsProperties() {
			DynamoDbPersistentEntity<?> entity = entityFor(Match.class);
			KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

			Match reconstructed = new Match();
			reconstructed.tournamentId = TOURNAMENT_ID;

			resolver.decomposeOnto(COLUMN_SK, COMPOSED_SK, reconstructed, new DefaultConversionService());

			assertAll("decomposed properties", () -> assertEquals(YEAR_2024, reconstructed.year),
					() -> assertEquals(ROUND_QUARTERFINAL, reconstructed.round));
		}
	}

	@Nested
	@DisplayName("Round-trip")
	class RoundTrip {

		@Test
		@DisplayName("Compose then decompose round-trips to the same values")
		void composeThenDecompose_roundTrips() {
			DynamoDbPersistentEntity<?> entity = entityFor(Match.class);
			KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

			Match original = new Match(TOURNAMENT_ID, YEAR_2025, "PENDING");
			String composed = resolver.compose(COLUMN_SK, original, new DefaultConversionService());

			Match reconstructed = new Match();
			resolver.decomposeOnto(COLUMN_SK, composed, reconstructed, new DefaultConversionService());

			assertAll("round-tripped values", () -> assertEquals(original.year, reconstructed.year),
					() -> assertEquals(original.round, reconstructed.round));
		}
	}

	@Nested
	@DisplayName("No template")
	class NoTemplate {

		@Test
		@DisplayName("Entity with no @SortKeyTemplate has no template for any index")
		void noTemplate_hasNoTemplateForAnyIndex() {
			DynamoDbPersistentEntity<?> entity = entityFor(PlainEntity.class);
			KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

			assertAll("no template present", () -> assertFalse(resolver.hasTemplate(COLUMN_SK)),
					() -> assertNull(resolver.templateFor(COLUMN_SK)),
					() -> assertNull(resolver.compose(COLUMN_SK, new PlainEntity(), new DefaultConversionService())));
		}
	}

	@Nested
	@DisplayName("Overloaded column")
	class OverloadedColumn {

		@Test
		@DisplayName("Template can target an overloaded column other than sk")
		void template_overloadedColumn_composesCorrectly() {
			DynamoDbPersistentEntity<?> entity = entityFor(MatchWithOverloadedColumnTemplate.class);
			KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

			MatchWithOverloadedColumnTemplate match = new MatchWithOverloadedColumnTemplate();
			match.tournamentId = TOURNAMENT_ID;
			match.year = YEAR_2024;
			match.round = ROUND_QUARTERFINAL;

			assertAll("overloaded column compose",
					() -> assertEquals("ROUND#QUARTERFINAL#2024",
							resolver.compose(COLUMN_GSI1SK, match, new DefaultConversionService())),
					() -> assertEquals(COLUMN_GSI1SK, resolver.columnFor(COLUMN_GSI1SK)));
		}
	}

	@Nested
	@DisplayName("Validation")
	class Validation {

		@Test
		@DisplayName("Template referencing an unknown property fails fast at bootstrap")
		void unknownProperty_failsFastAtBootstrap() {
			@Table(tableName = "bad")
			@SortKeyTemplate("MATCH#{doesNotExist}")
			class BadEntity {
				@PartitionKey
				String pk;
			}

			DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();

			assertThrows(MappingException.class, () -> mappingContext.getRequiredPersistentEntity(BadEntity.class));
		}
	}

	@Nested
	@DisplayName("Prefix query")
	class PrefixQuery {

		@Test
		@DisplayName("Supports a prefix query on a leading subset of placeholders")
		void prefixFor_leadingSubset_producesValidPrefix() {
			DynamoDbPersistentEntity<?> entity = entityFor(Match.class);
			KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

			String prefix = resolver.templateFor(COLUMN_SK).prefixFor(Map.of("year", YEAR_2024));

			assertAll("prefix query", () -> assertEquals("MATCH#2024#", prefix),
					() -> assertTrue("MATCH#2024#QUARTERFINAL".startsWith(prefix)),
					() -> assertFalse("MATCH#20245#QUARTERFINAL".startsWith(prefix)));
		}
	}
}
