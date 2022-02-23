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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.cloud.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.dynamodb.core.mapping.InnerClass;
import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import io.awspring.cloud.dynamodb.entities.arena.MatchStatus;
import io.awspring.cloud.dynamodb.entities.arena.Venue;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class TournamentDomainConverterTest {

	@Table(tableName = "scheduled_match")
	public static class ScheduledMatch {
		@PartitionKey
		private String tournamentId;
		@SortKey
		private String matchId;
		private MatchStatus status;
		private LocalDate scheduledAt;
		private int bestOf;
		@InnerClass(serializeAsJson = true)
		private Venue venue;

		public ScheduledMatch() {
		}

		public ScheduledMatch(String tournamentId, String matchId, MatchStatus status, LocalDate scheduledAt,
				int bestOf, Venue venue) {
			this.tournamentId = tournamentId;
			this.matchId = matchId;
			this.status = status;
			this.scheduledAt = scheduledAt;
			this.bestOf = bestOf;
			this.venue = venue;
		}

		public String getTournamentId() {
			return tournamentId;
		}

		public void setTournamentId(String tournamentId) {
			this.tournamentId = tournamentId;
		}

		public String getMatchId() {
			return matchId;
		}

		public void setMatchId(String matchId) {
			this.matchId = matchId;
		}

		public MatchStatus getStatus() {
			return status;
		}

		public void setStatus(MatchStatus status) {
			this.status = status;
		}

		public LocalDate getScheduledAt() {
			return scheduledAt;
		}

		public void setScheduledAt(LocalDate scheduledAt) {
			this.scheduledAt = scheduledAt;
		}

		public int getBestOf() {
			return bestOf;
		}

		public void setBestOf(int bestOf) {
			this.bestOf = bestOf;
		}

		public Venue getVenue() {
			return venue;
		}

		public void setVenue(Venue venue) {
			this.venue = venue;
		}
	}

	private MappingDynamoDbConverter converter;

	@BeforeEach
	void setUp() {
		converter = new MappingDynamoDbConverter(new DynamoDbMappingContext());
		converter.afterPropertiesSet();
	}

	private ScheduledMatch write(ScheduledMatch match, Map<String, AttributeValue> item) {
		converter.write(match, item);
		return match;
	}

	@Test
	void aFullyPopulatedMatchWritesAllKeyAndScalarColumnsAndRoundTrips() {
		LocalDate day = LocalDate.of(2026, 3, 14);
		Venue venue = new Venue("Spodek", 11000L, "Katowice", "PL");
		ScheduledMatch match = new ScheduledMatch("winter2026", "m-1", MatchStatus.LIVE, day, 5, venue);

		Map<String, AttributeValue> item = new HashMap<>();
		write(match, item);

		assertEquals("winter2026", item.get("tournamentId").s());
		assertEquals("m-1", item.get("matchId").s());
		assertEquals("5", item.get("bestOf").n());
		assertTrue(item.containsKey("status"));
		assertTrue(item.containsKey("scheduledAt"));
		assertTrue(item.containsKey("venue"));

		ScheduledMatch readBack = converter.read(ScheduledMatch.class, item);
		assertEquals("winter2026", readBack.getTournamentId());
		assertEquals("m-1", readBack.getMatchId());
		assertEquals(MatchStatus.LIVE, readBack.getStatus());
		assertEquals(day, readBack.getScheduledAt());
		assertEquals(5, readBack.getBestOf());
		assertEquals(venue, readBack.getVenue());
	}

	@ParameterizedTest
	@EnumSource(MatchStatus.class)
	void everyMatchStatusRoundTripsThroughTheConverter(MatchStatus status) {
		ScheduledMatch match = new ScheduledMatch("winter2026", "m-status", status, LocalDate.of(2026, 1, 1), 3, null);

		Map<String, AttributeValue> item = new HashMap<>();
		write(match, item);

		ScheduledMatch readBack = converter.read(ScheduledMatch.class, item);
		assertEquals(status, readBack.getStatus());
	}

	@Test
	void aMatchWithNoVenueOmitsTheJsonColumnAndReadsBackAsNull() {
		ScheduledMatch match = new ScheduledMatch("winter2026", "m-2", MatchStatus.SCHEDULED, LocalDate.of(2026, 2, 2),
				1, null);

		Map<String, AttributeValue> item = new HashMap<>();
		write(match, item);

		assertFalse(item.containsKey("venue") && item.get("venue").s() != null && !item.get("venue").s().isEmpty()
				&& !"null".equals(item.get("venue").s()));

		ScheduledMatch readBack = converter.read(ScheduledMatch.class, item);
		assertNull(readBack.getVenue());
	}

	@Test
	void aNullStatusIsNotMaterialisedAndReadsBackAsNull() {
		ScheduledMatch match = new ScheduledMatch("winter2026", "m-3", null, LocalDate.of(2026, 4, 4), 3,
				new Venue("Arena", 5000L, "Berlin", "DE"));

		Map<String, AttributeValue> item = new HashMap<>();
		write(match, item);

		AttributeValue status = item.get("status");
		assertTrue(status == null || Boolean.TRUE.equals(status.nul()));

		ScheduledMatch readBack = converter.read(ScheduledMatch.class, item);
		assertNull(readBack.getStatus());
		assertEquals(match.getVenue(), readBack.getVenue());
	}

	@Test
	void theStatusColumnIsStoredAsAStringHoldingTheEnumConstantName() {
		ScheduledMatch match = new ScheduledMatch("winter2026", "m-4", MatchStatus.COMPLETED, LocalDate.of(2026, 5, 5),
				3, null);

		Map<String, AttributeValue> item = new HashMap<>();
		write(match, item);

		assertEquals(MatchStatus.COMPLETED.name(), item.get("status").s());
	}
}
