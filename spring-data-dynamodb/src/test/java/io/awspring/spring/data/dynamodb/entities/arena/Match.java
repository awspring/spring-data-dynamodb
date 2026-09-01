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
package io.awspring.spring.data.dynamodb.entities.arena;

import io.awspring.spring.data.dynamodb.core.mapping.Embedded;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class Match {

	private String tournamentId;
	private UUID matchId;
	private MatchStatus round;
	private LocalDate scheduledAt;

	@Embedded(serializeAsNestedMap = true)
	private Venue venue;

	public Match() {
	}

	public Match(String tournamentId, UUID matchId, MatchStatus round, LocalDate scheduledAt, Venue venue) {
		this.tournamentId = tournamentId;
		this.matchId = matchId;
		this.round = round;
		this.scheduledAt = scheduledAt;
		this.venue = venue;
	}

	public String getTournamentId() {
		return tournamentId;
	}

	public void setTournamentId(String tournamentId) {
		this.tournamentId = tournamentId;
	}

	public UUID getMatchId() {
		return matchId;
	}

	public void setMatchId(UUID matchId) {
		this.matchId = matchId;
	}

	public MatchStatus getStatus() {
		return round;
	}

	public void setStatus(MatchStatus round) {
		this.round = round;
	}

	public LocalDate getScheduledAt() {
		return scheduledAt;
	}

	public void setScheduledAt(LocalDate scheduledAt) {
		this.scheduledAt = scheduledAt;
	}

	public Venue getVenue() {
		return venue;
	}

	public void setVenue(Venue venue) {
		this.venue = venue;
	}

	@Override
	public boolean equals(Object o) {
		Match match = (Match) o;
		return Objects.equals(tournamentId, match.tournamentId) && Objects.equals(matchId, match.matchId)
				&& Objects.equals(round, match.round) && Objects.equals(scheduledAt, match.scheduledAt)
				&& Objects.equals(venue, match.venue);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tournamentId, matchId, round, scheduledAt, venue);
	}
}
