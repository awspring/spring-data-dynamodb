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
package io.awspring.spring.data.dynamodb.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.awspring.spring.data.dynamodb.core.mapping.KeyTemplate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KeyTemplate -- parse / compose / decompose / prefix")
class KeyTemplateTest {

	@Test
	void parsesLiteralAndPlaceholderSegmentsInOrder() {
		KeyTemplate template = KeyTemplate.parse("MATCH#{year}#{round}");
		assertEquals(List.of("year", "round"), template.placeholderNames());
	}

	@Test
	void composesThePhysicalStringFromBoundValues() {
		KeyTemplate template = KeyTemplate.parse("MATCH#{year}#{round}");
		String composed = template.compose(Map.of("year", 2024, "round", "QUARTERFINAL"));
		assertEquals("MATCH#2024#QUARTERFINAL", composed);
	}

	@Test
	void decomposeRecoversEveryPlaceholderValueAsAString() {
		KeyTemplate template = KeyTemplate.parse("MATCH#{year}#{round}");
		Map<String, String> decomposed = template.decompose("MATCH#2024#QUARTERFINAL");
		assertEquals(Map.of("year", "2024", "round", "QUARTERFINAL"), decomposed);
	}

	@Test
	void decomposeIsTheExactInverseOfCompose() {
		KeyTemplate template = KeyTemplate.parse("CUSTOMER#{tournamentId}#MATCH#{matchId}");
		String composed = template.compose(Map.of("tournamentId", "cust-1", "matchId", "match-42"));
		assertEquals("CUSTOMER#cust-1#MATCH#match-42", composed);
		assertEquals(Map.of("tournamentId", "cust-1", "matchId", "match-42"), template.decompose(composed));
	}

	@Test
	void decomposeRejectsAStringThatDoesNotMatchTheTemplatesLiterals() {
		KeyTemplate template = KeyTemplate.parse("MATCH#{year}#{round}");
		assertThrows(IllegalStateException.class, () -> template.decompose("PAYMENT#2024#QUARTERFINAL"));
	}

	@Test
	void prefixForALeadingSubsetOfPlaceholdersIncludesTheTrailingLiteral() {
		KeyTemplate template = KeyTemplate.parse("MATCH#{year}#{round}");
		String prefix = template.prefixFor(Map.of("year", 2024));
		assertEquals("MATCH#2024#", prefix);
	}

	@Test
	void prefixForNoPlaceholdersBoundIsJustTheLeadingLiteral() {
		KeyTemplate template = KeyTemplate.parse("MATCH#{year}#{round}");
		assertEquals("MATCH#", template.prefixFor(Map.of()));
	}

	@Test
	void prefixForEveryPlaceholderBoundEqualsTheFullComposedString() {
		KeyTemplate template = KeyTemplate.parse("MATCH#{year}#{round}");
		Map<String, Object> values = Map.of("year", 2024, "round", "QUARTERFINAL");
		assertEquals(template.compose(values), template.prefixFor(values));
	}

	@Test
	void composeRejectsAMissingPlaceholderValue() {
		KeyTemplate template = KeyTemplate.parse("MATCH#{year}#{round}");
		assertThrows(IllegalStateException.class, () -> template.compose(Map.of("year", 2024)));
	}

	@Test
	void aTemplateWithNoPlaceholderIsRejectedAtParseTime() {
		assertThrows(IllegalStateException.class, () -> KeyTemplate.parse("JUST-A-LITERAL"));
	}

	@Test
	void aTrailingPlaceholderWithNoFollowingLiteralDecomposesToEndOfString() {
		KeyTemplate template = KeyTemplate.parse("MATCH#{matchId}");
		assertEquals(Map.of("matchId", "42"), template.decompose("MATCH#42"));
	}

	@Test
	void adjacentPlaceholdersRemainAllowed() {
		KeyTemplate template = KeyTemplate.parse("MATCH#{year}{round}");
		assertEquals("MATCH#2024FINAL", template.compose(Map.of("year", 2024, "round", "FINAL")));
	}

	@Test
	void repeatedPlaceholdersRemainAllowed() {
		KeyTemplate template = KeyTemplate.parse("MATCH#{id}#COPY#{id}");
		assertEquals("MATCH#42#COPY#42", template.compose(Map.of("id", 42)));
	}

	@Test
	void decomposeRemainsPermissiveAboutContentAfterATrailingLiteral() {
		KeyTemplate template = KeyTemplate.parse("MATCH#{matchId}#END");
		assertEquals(Map.of("matchId", "42"), template.decompose("MATCH#42#END#EXTRA"));
	}
}
