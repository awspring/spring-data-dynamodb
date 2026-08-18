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
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.mapping.MappingException;
import org.springframework.util.StringUtils;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class SortKeyTemplateVerifier implements DynamoDbPersistentEntityMetadataVerifier {

	@Override
	public void verify(DynamoDbPersistentEntity<?> entity) throws MappingException {
		if (entity.getType().isInterface() || !entity.isAnnotationPresent(Table.class)) {
			return;
		}

		List<SortKeyTemplate> templates = new ArrayList<>(AnnotatedElementUtils
				.findMergedRepeatableAnnotations(entity.getType(), SortKeyTemplate.class, SortKeyTemplate.List.class));

		if (templates.isEmpty()) {
			return;
		}

		List<MappingException> exceptions = new ArrayList<>();

		IndexKeySchema schema = entity.getKeySchema();
		for (SortKeyTemplate template : templates) {
			String column = StringUtils.hasText(template.column()) ? template.column() : "sk";
			boolean conflicts = schema.sortKeys().stream()
					.anyMatch(sortKeyProperty -> sortKeyProperty.getColumnName().equals(column));
			if (conflicts) {
				String sortKeyNames = schema.sortKeys().stream().filter(p -> p.getColumnName().equals(column))
						.map(DynamoDbPersistentProperty::getName).collect(Collectors.joining(", "));
				exceptions.add(new MappingException(String.format(
						"%s declares both a @SortKey-derived sort key (%s) and a @SortKeyTemplate (\"%s\") "
								+ "both targeting column \"%s\"; the two are mutually exclusive per column -- "
								+ "use one or the other, or target a different column()",
						entity.getType().getName(), sortKeyNames, template.value(), column)));
			}
		}

		try {
			KeyTemplateResolver.forEntity(entity);
		}
		catch (IllegalStateException ex) {
			exceptions.add(new MappingException(ex.getMessage(), ex));
		}

		if (!exceptions.isEmpty()) {
			throw new VerifierMappingExceptions(entity, exceptions);
		}
	}
}
