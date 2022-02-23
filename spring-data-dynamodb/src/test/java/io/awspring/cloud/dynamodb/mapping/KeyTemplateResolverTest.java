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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbPersistentEntity;
import io.awspring.cloud.dynamodb.core.mapping.KeyTemplateResolver;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKeyTemplate;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.mapping.MappingException;

public class KeyTemplateResolverTest {

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

	@Test
	void composesTheBaseTableSortKeyFromBoundProperties() {
		DynamoDbPersistentEntity<?> entity = entityFor(Match.class);
		KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

		Match match = new Match("cust-1", 2024, "QUARTERFINAL");
		String sortKey = resolver.compose("sk", match, new DefaultConversionService());

		assertEquals("MATCH#2024#QUARTERFINAL", sortKey);
		assertEquals("sk", resolver.columnFor("sk"));
	}

	@Test
	void decomposesThePhysicalStringBackOntoTheInstancesProperties() {
		DynamoDbPersistentEntity<?> entity = entityFor(Match.class);
		KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

		Match reconstructed = new Match();
		reconstructed.tournamentId = "cust-1";
		resolver.decomposeOnto("sk", "MATCH#2024#QUARTERFINAL", reconstructed, new DefaultConversionService());

		assertEquals(2024, reconstructed.year);
		assertEquals("QUARTERFINAL", reconstructed.round);
	}

	@Test
	void composeThenDecomposeRoundTripsToTheSameValues() {
		DynamoDbPersistentEntity<?> entity = entityFor(Match.class);
		KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

		Match original = new Match("cust-1", 2025, "PENDING");
		String composed = resolver.compose("sk", original, new DefaultConversionService());

		Match reconstructed = new Match();
		resolver.decomposeOnto("sk", composed, reconstructed, new DefaultConversionService());

		assertEquals(original.year, reconstructed.year);
		assertEquals(original.round, reconstructed.round);
	}

	@Test
	void anEntityWithNoSortKeyTemplateHasNoTemplateForAnyIndex() {
		DynamoDbPersistentEntity<?> entity = entityFor(PlainEntity.class);
		KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

		assertFalse(resolver.hasTemplate("sk"));
		assertNull(resolver.templateFor("sk"));
		assertNull(resolver.compose("sk", new PlainEntity(), new DefaultConversionService()));
	}

	@Test
	void aTemplateCanTargetAnOverloadedColumnOtherThanSk() {
		DynamoDbPersistentEntity<?> entity = entityFor(MatchWithOverloadedColumnTemplate.class);
		KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

		MatchWithOverloadedColumnTemplate match = new MatchWithOverloadedColumnTemplate();
		match.tournamentId = "cust-1";
		match.year = 2024;
		match.round = "QUARTERFINAL";

		assertEquals("ROUND#QUARTERFINAL#2024", resolver.compose("gsi1sk", match, new DefaultConversionService()));
		assertEquals("gsi1sk", resolver.columnFor("gsi1sk"));
	}

	@Test
	void aTemplateReferencingAnUnknownPropertyFailsFastAtBootstrap() {
		@Table(tableName = "bad")
		@SortKeyTemplate("MATCH#{doesNotExist}")
		class BadEntity {
			@PartitionKey
			String pk;
		}
		DynamoDbMappingContext mappingContext = new DynamoDbMappingContext();
		assertThrows(MappingException.class, () -> mappingContext.getRequiredPersistentEntity(BadEntity.class));
	}

	@Test
	void supportsAPrefixQueryOnALeadingSubsetOfTheTemplatesPlaceholders() {
		DynamoDbPersistentEntity<?> entity = entityFor(Match.class);
		KeyTemplateResolver resolver = KeyTemplateResolver.forEntity(entity);

		String prefix = resolver.templateFor("sk").prefixFor(Map.of("year", 2024));
		assertEquals("MATCH#2024#", prefix);
		assertTrue("MATCH#2024#QUARTERFINAL".startsWith(prefix));
		assertFalse("MATCH#20245#QUARTERFINAL".startsWith(prefix));
	}
}
