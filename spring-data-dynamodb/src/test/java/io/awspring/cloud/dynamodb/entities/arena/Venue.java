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

import java.util.Objects;

public class Venue {
	private String name;
	private Long capacity;
	private String city;
	private String country;

	public Venue() {
	}

	public Venue(String name, Long capacity, String city, String country) {
		this.name = name;
		this.capacity = capacity;
		this.city = city;
		this.country = country;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Long getCapacity() {
		return capacity;
	}

	public void setCapacity(Long capacity) {
		this.capacity = capacity;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Venue venue = (Venue) o;
		return Objects.equals(name, venue.name) && Objects.equals(capacity, venue.capacity)
				&& Objects.equals(city, venue.city) && Objects.equals(country, venue.country);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, capacity, city, country);
	}
}
