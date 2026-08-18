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
package io.awspring.cloud.dynamodb.core.mapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.Assert;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public final class KeyTemplate {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");

	private final String raw;
	private final List<Segment> segments;

	private KeyTemplate(String raw, List<Segment> segments) {
		this.raw = raw;
		this.segments = segments;
	}

	public record Segment(boolean literal, String text) {
		static Segment literal(String text) {
			return new Segment(true, text);
		}
		static Segment placeholder(String name) {
			return new Segment(false, name);
		}
	}

	public static KeyTemplate parse(String template) {
		Assert.hasText(template, "template must not be empty");

		List<Segment> segments = new ArrayList<>();
		Matcher matcher = PLACEHOLDER.matcher(template);
		int matchedUntil = 0;
		while (matcher.find()) {
			if (matcher.start() > matchedUntil) {
				segments.add(Segment.literal(template.substring(matchedUntil, matcher.start())));
			}
			segments.add(Segment.placeholder(matcher.group(1)));
			matchedUntil = matcher.end();
		}
		if (matchedUntil < template.length()) {
			segments.add(Segment.literal(template.substring(matchedUntil)));
		}
		Assert.state(segments.stream().anyMatch(s -> !s.literal()),
				() -> "SortKeyTemplate \"" + template + "\" has no {placeholder} -- nothing to compose");
		return new KeyTemplate(template, List.copyOf(segments));
	}

	public String raw() {
		return raw;
	}

	public List<String> placeholderNames() {
		List<String> names = new ArrayList<>();
		for (Segment segment : segments) {
			if (!segment.literal()) {
				names.add(segment.text());
			}
		}
		return names;
	}

	public String compose(Map<String, Object> values) {
		StringBuilder result = new StringBuilder();
		for (Segment segment : segments) {
			if (segment.literal()) {
				result.append(segment.text());
			}
			else {
				Object value = values.get(segment.text());
				Assert.state(value != null, () -> "SortKeyTemplate \"" + raw + "\" requires a value for placeholder {"
						+ segment.text() + "} -- none was bound");
				result.append(value);
			}
		}
		return result.toString();
	}

	public String prefixFor(Map<String, Object> values) {
		StringBuilder result = new StringBuilder();
		for (Segment segment : segments) {
			if (segment.literal()) {
				result.append(segment.text());
				continue;
			}
			Object value = values.get(segment.text());
			if (value == null) {
				break;
			}
			result.append(value);
		}
		return result.toString();
	}

	public Map<String, String> decompose(String physical) {
		Map<String, String> result = new LinkedHashMap<>();
		int cursor = 0;
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			final int segmentStart = cursor;
			if (segment.literal()) {
				Assert.state(physical.startsWith(segment.text(), segmentStart),
						() -> "\"" + physical + "\" does not match SortKeyTemplate \"" + raw
								+ "\" -- expected literal \"" + segment.text() + "\" at position " + segmentStart);
				cursor += segment.text().length();
				continue;
			}
			String nextLiteral = nextLiteralAfter(i);
			int end = nextLiteral.isEmpty() ? physical.length() : physical.indexOf(nextLiteral, cursor);
			final int foundAt = end;
			Assert.state(foundAt >= 0, () -> "\"" + physical + "\" does not match SortKeyTemplate \"" + raw
					+ "\" -- could not find the literal following {" + segment.text() + "}");
			result.put(segment.text(), physical.substring(cursor, end));
			cursor = end;
		}
		return result;
	}

	private String nextLiteralAfter(int index) {
		for (int i = index + 1; i < segments.size(); i++) {
			if (segments.get(i).literal()) {
				return segments.get(i).text();
			}
		}
		return "";
	}
}
