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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.Nullable;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.util.StringUtils;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class CachingDynamoDbPersistentProperty extends BasicDynamoDbPersistentProperty {

	private final boolean isEmbedded;
	private Class<?> typeOfProperty;

	private String startsWith;

	private String endsWith;

	private @Nullable Pattern regexPattern;

	private boolean serializeAsNestedMap = false;

	private boolean isSpecialType = false;

	public CachingDynamoDbPersistentProperty(Property property, DynamoDbPersistentEntity<?> owner,
			SimpleTypeHolder simpleTypeHolder) {
		super(property, owner, simpleTypeHolder);
		this.isEmbedded = super.isEntity();

		if (isEmbedded) {
			Type ty = property.getField().get().getGenericType();
			if (ty instanceof ParameterizedType parameterizedType) {
				Type typeArgument = parameterizedType.getActualTypeArguments()[0];
				this.typeOfProperty = resolveClass(typeArgument, property);
			}
			else if (property.getField().get().isAnnotationPresent(InnerClass.class)) {
				InnerClass innerClass = property.getField().get().getAnnotation(InnerClass.class);
				startsWith = innerClass.startsWith();
				endsWith = innerClass.endsWith();
				regexPattern = compile(innerClass.regex(), property);
				serializeAsNestedMap = innerClass.serializeAsNestedMap();
				this.typeOfProperty = resolveClass(ty, property);
				this.isSpecialType = true;
			}
		}
		else {
			typeOfProperty = null;
		}
	}

	private static Class<?> resolveClass(Type type, Property property) {
		if (type instanceof Class<?> resolvedClass) {
			return resolvedClass;
		}
		if (type instanceof ParameterizedType parameterizedType
				&& parameterizedType.getRawType() instanceof Class<?> resolvedClass) {
			return resolvedClass;
		}
		throw new MappingException("Cannot resolve the mapped type of " + property + " from " + type.getTypeName());
	}

	@Nullable
	private static Pattern compile(String regex, Property property) {
		if (!StringUtils.hasText(regex)) {
			return null;
		}
		try {
			return Pattern.compile(regex);
		}
		catch (PatternSyntaxException e) {
			throw new MappingException(
					"@InnerClass(regex = \"" + regex + "\") on " + property + " is not a valid regular expression", e);
		}
	}

	@Override
	public String startsWith() {
		return this.startsWith;
	}

	@Override
	public String endsWith() {
		return this.endsWith;
	}

	@Override
	@Nullable
	public Pattern regexPattern() {
		return this.regexPattern;
	}

	@Override
	public Class<?> getTypeOfProperty() {
		return typeOfProperty;
	}

	@Override
	public boolean isSpecialType() {
		return this.isSpecialType;
	}

	@Override
	public boolean serializeAsNestedMap() {
		return this.serializeAsNestedMap;
	}
}
