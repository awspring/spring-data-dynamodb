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

import io.awspring.spring.data.dynamodb.core.mapping.InnerClass;
import io.awspring.spring.data.dynamodb.core.mapping.PartitionKey;
import io.awspring.spring.data.dynamodb.core.mapping.SortKey;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import java.util.Objects;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
@Table(tableName = "arena")
public class MatchTable {
	@PartitionKey
	private String partitionKey;
	@SortKey
	private String sortKey;
	@InnerClass(startsWith = "MATCH")
	private MatchSK match;
	@InnerClass(startsWith = "PLAYER")
	private PlayerContact playerContact;

	public MatchTable() {
	}

	public MatchTable(String partitionKey, String sortKey, MatchSK match, PlayerContact playerContact) {
		this.partitionKey = partitionKey;
		this.sortKey = sortKey;
		this.match = match;
		this.playerContact = playerContact;
	}

	public String getPartitionKey() {
		return partitionKey;
	}

	public void setPartitionKey(String partitionKey) {
		this.partitionKey = partitionKey;
	}

	public String getSortKey() {
		return sortKey;
	}

	public void setSortKey(String sortKey) {
		this.sortKey = sortKey;
	}

	public MatchSK getMatch() {
		return match;
	}

	public void setMatch(MatchSK match) {
		this.match = match;
	}

	public PlayerContact getPlayerContact() {
		return playerContact;
	}

	public void setPlayerContact(PlayerContact playerContact) {
		this.playerContact = playerContact;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		MatchTable matchTable = (MatchTable) o;
		return Objects.equals(partitionKey, matchTable.partitionKey) && Objects.equals(sortKey, matchTable.sortKey)
				&& Objects.equals(match, matchTable.match) && Objects.equals(playerContact, matchTable.playerContact);
	}

	@Override
	public int hashCode() {
		return Objects.hash(partitionKey, sortKey, match, playerContact);
	}
}
