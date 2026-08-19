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
package io.awspring.spring.data.dynamodb.core.mapping;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.model.ConvertingPropertyAccessor;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public final class KeyTemplateResolver {

	private final DynamoDbPersistentEntity<?> entity;
	private final Map<String, KeyTemplate> templatesByIndex;
	private final Map<String, String> columnsByIndex;

	private KeyTemplateResolver(DynamoDbPersistentEntity<?> entity, Map<String, KeyTemplate> templatesByIndex,
			Map<String, String> columnsByIndex) {
		this.entity = entity;
		this.templatesByIndex = templatesByIndex;
		this.columnsByIndex = columnsByIndex;
	}

	public static KeyTemplateResolver forEntity(DynamoDbPersistentEntity<?> entity) {

		Map<String, KeyTemplate> templates = new LinkedHashMap<>();
		Map<String, String> columns = new LinkedHashMap<>();

		for (SortKeyTemplate annotation : AnnotatedElementUtils.findMergedRepeatableAnnotations(entity.getType(),
				SortKeyTemplate.class, SortKeyTemplate.List.class)) {

			String column = StringUtils.hasText(annotation.column()) ? annotation.column() : "sk";
			Assert.state(!templates.containsKey(column),
					() -> "Entity " + entity.getType().getName()
							+ " declares more than one @SortKeyTemplate targeting column \"" + column
							+ "\"; each @SortKeyTemplate must target a distinct column()");

			KeyTemplate template = KeyTemplate.parse(annotation.value());
			for (String placeholder : template.placeholderNames()) {
				Assert.state(entity.getPersistentProperty(placeholder) != null,
						() -> "@SortKeyTemplate \"" + annotation.value() + "\" on " + entity.getType().getName()
								+ " references unknown property \"" + placeholder + "\"");
			}

			templates.put(column, template);
			columns.put(column, column);
		}

		return new KeyTemplateResolver(entity, Map.copyOf(templates), Map.copyOf(columns));
	}

	public boolean hasTemplate(String index) {
		return templatesByIndex.containsKey(index);
	}

	public Set<String> templateIndexes() {
		return templatesByIndex.keySet();
	}

	@Nullable
	public KeyTemplate templateFor(String index) {
		return templatesByIndex.get(index);
	}

	@Nullable
	public String columnFor(String index) {
		return columnsByIndex.get(index);
	}

	@Nullable
	public String compose(String index, Object instance, ConversionService conversionService) {
		KeyTemplate template = templatesByIndex.get(index);
		if (template == null) {
			return null;
		}
		PersistentPropertyAccessor<Object> accessor = entity.getPropertyAccessor(instance);
		ConvertingPropertyAccessor<Object> converting = new ConvertingPropertyAccessor<>(accessor, conversionService);
		Map<String, Object> values = new LinkedHashMap<>();
		for (String placeholder : template.placeholderNames()) {
			DynamoDbPersistentProperty property = requireProperty(placeholder);
			values.put(placeholder, converting.getProperty(property));
		}
		return template.compose(values);
	}

	public void decomposeOnto(String index, String physical, Object instance, ConversionService conversionService) {
		KeyTemplate template = templatesByIndex.get(index);
		if (template == null) {
			return;
		}
		PersistentPropertyAccessor<Object> accessor = entity.getPropertyAccessor(instance);
		ConvertingPropertyAccessor<Object> converting = new ConvertingPropertyAccessor<>(accessor, conversionService);
		Map<String, String> decomposed = template.decompose(physical);
		for (Map.Entry<String, String> entry : decomposed.entrySet()) {
			DynamoDbPersistentProperty property = requireProperty(entry.getKey());
			converting.setProperty(property, entry.getValue());
		}
	}

	private DynamoDbPersistentProperty requireProperty(String name) {
		DynamoDbPersistentProperty property = entity.getPersistentProperty(name);
		Assert.state(property != null, () -> "SortKeyTemplate placeholder {" + name + "} has no matching property on "
				+ entity.getType().getName());
		return property;
	}
}
