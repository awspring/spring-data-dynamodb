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

import io.awspring.cloud.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.dynamodb.core.mapping.Table;
import java.time.LocalDate;
import java.util.*;

@Table(tableName = "playerCardEntity")
public class PlayerCardEntity {

	@PartitionKey
	private String id;

	private LocalDate registeredOn;

	private List<String> tags;

	private List<String> aliases;

	public PlayerCardEntity() {
	}

	public PlayerCardEntity(String id, LocalDate registeredOn) {
		this.id = id;
		this.registeredOn = registeredOn;
		this.tags = new ArrayList<>();
		tags.add("test");
	}

	public PlayerCardEntity(String id, LocalDate registeredOn, List<String> tags) {
		this.tags = tags;
		this.id = id;
		this.registeredOn = registeredOn;
	}

	public PlayerCardEntity(String id, LocalDate registeredOn, List<String> tags, List<String> aliases) {
		this.aliases = aliases;
		this.tags = tags;
		this.id = id;
		this.registeredOn = registeredOn;
	}

	public List<String> getAliases() {
		return aliases;
	}

	public void setAliases(List<String> aliases) {
		this.aliases = aliases;
	}

	public List<String> getTags() {
		return tags;
	}

	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public LocalDate getRegisteredOn() {
		return registeredOn;
	}

	public void setRegisteredOn(LocalDate registeredOn) {
		this.registeredOn = registeredOn;
	}
}
