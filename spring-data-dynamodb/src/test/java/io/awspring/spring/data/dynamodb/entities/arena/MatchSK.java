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

import io.awspring.spring.data.dynamodb.core.mapping.Column;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class MatchSK extends Match {

	@Column("GLOBAL_SK_1")
	private String globalSortKey;

	public MatchSK() {
	}

	public MatchSK(String tournamentId, UUID matchId, MatchStatus round, LocalDate scheduledAt, Venue venue,
			String globalSortKey) {
		super(tournamentId, matchId, round, scheduledAt, venue);
		this.globalSortKey = globalSortKey;
	}

	public String getGlobalSortKey() {
		return globalSortKey;
	}

	public void setGlobalSortKey(String globalSortKey) {
		this.globalSortKey = globalSortKey;
	}

	@Override
	public boolean equals(Object o) {
		if (!super.equals(o))
			return false;
		MatchSK matchSK = (MatchSK) o;
		return Objects.equals(globalSortKey, matchSK.globalSortKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), globalSortKey);
	}
}
