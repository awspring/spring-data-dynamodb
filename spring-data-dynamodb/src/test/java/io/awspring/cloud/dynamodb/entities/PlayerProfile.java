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
package io.awspring.cloud.dynamodb.entities;

import io.awspring.cloud.dynamodb.core.mapping.Column;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class PlayerProfile {

	@Column("contactHandle")
	public String contactHandle;
	public Long points;
	public List<String> achievements;
	public HashMap<String, List<String>> statsByGame;

	public PlayerProfile() {
		contactHandle = "09";
		points = 1L;
		achievements = Collections.singletonList("dva");
		statsByGame = new HashMap<>();
	}

	public HashMap<String, List<String>> getStatsByGame() {
		return statsByGame;
	}

	public void setStatsByGame(HashMap<String, List<String>> statsByGame) {
		this.statsByGame = statsByGame;
	}

	public String getContactHandle() {
		return contactHandle;
	}

	public void setContactHandle(String contactHandle) {
		this.contactHandle = contactHandle;
	}

	public Long getPoints() {
		return points;
	}

	public void setPoints(Long points) {
		this.points = points;
	}

	public List<String> getAchievements() {
		return achievements;
	}

	public void setAchievements(List<String> achievements) {
		this.achievements = achievements;
	}
}
