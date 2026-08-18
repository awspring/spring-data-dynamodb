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
package io.awspring.cloud.dynamodb.entities.arena;

import io.awspring.cloud.dynamodb.core.mapping.AggregateItem;
import io.awspring.cloud.dynamodb.core.mapping.AggregateTable;
import java.util.List;

@AggregateTable(tableName = "arena", partitionKey = "partitionKey", sortKey = "sortKey")
public class MatchAggregate {

	@AggregateItem(startsWith = "MATCH")
	private List<MatchSkAggregate> match;
	@AggregateItem(startsWith = "PLAYER")
	private PlayerContactAggregate playerContact;

	public List<MatchSkAggregate> getMatch() {
		return match;
	}

	public void setMatch(List<MatchSkAggregate> match) {
		this.match = match;
	}

	public PlayerContactAggregate getPlayerContact() {
		return playerContact;
	}

	public void setPlayerContact(PlayerContactAggregate playerContact) {
		this.playerContact = playerContact;
	}
}
