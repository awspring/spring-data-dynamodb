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

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class PlayerContact {
	private String gamerTag;
	private String realName;
	private String email;
	private LocalDate joinedAt;
	@Embedded(serializeAsNestedMap = true)
	private Venue venue;

	public PlayerContact() {
	}

	public PlayerContact(String gamerTag, String realName, String email, LocalDate joinedAt, Venue venue) {
		this.gamerTag = gamerTag;
		this.realName = realName;
		this.email = email;
		this.joinedAt = joinedAt;
		this.venue = venue;
	}

	public String getGamerTag() {
		return gamerTag;
	}

	public void setGamerTag(String gamerTag) {
		this.gamerTag = gamerTag;
	}

	public String getRealName() {
		return realName;
	}

	public void setRealName(String realName) {
		this.realName = realName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public LocalDate getJoinedAt() {
		return joinedAt;
	}

	public void setJoinedAt(LocalDate joinedAt) {
		this.joinedAt = joinedAt;
	}

	public Venue getVenue() {
		return venue;
	}

	public void setVenue(Venue venue) {
		this.venue = venue;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		PlayerContact that = (PlayerContact) o;
		return Objects.equals(gamerTag, that.gamerTag) && Objects.equals(realName, that.realName)
				&& Objects.equals(email, that.email) && Objects.equals(joinedAt, that.joinedAt)
				&& Objects.equals(venue, that.venue);
	}

	@Override
	public int hashCode() {
		return Objects.hash(gamerTag, realName, email, joinedAt, venue);
	}
}
