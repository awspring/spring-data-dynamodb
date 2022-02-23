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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;

public class CachingDynamoDbPersistentProperty extends BasicDynamoDbPersistentProperty {

	private final boolean isEmbedded;
	private Class typeOfProperty;

	private String startsWith;

	private String endsWith;

	private boolean serializeAsJson = false;

	private boolean isSpecialType = false;

	public CachingDynamoDbPersistentProperty(Property property, DynamoDbPersistentEntity<?> owner,
			SimpleTypeHolder simpleTypeHolder) {
		super(property, owner, simpleTypeHolder);
		this.isEmbedded = super.isEntity();

		if (isEmbedded) {
			try {
				Type ty = property.getField().get().getGenericType();
				if (ty instanceof ParameterizedType) {
					Type t = ((ParameterizedType) ty).getActualTypeArguments()[0];
					this.typeOfProperty = Class.forName(((Class) t).getName());
				}
				else if (property.getField().get().isAnnotationPresent(InnerClass.class)) {
					startsWith = property.getField().get().getAnnotation(InnerClass.class).startsWith();
					endsWith = property.getField().get().getAnnotation(InnerClass.class).endsWith();
					serializeAsJson = property.getField().get().getAnnotation(InnerClass.class).serializeAsJson();
					this.typeOfProperty = Class.forName(((Class) ty).getName());
					this.isSpecialType = true;
				}
			}
			catch (ReflectiveOperationException e) {
				throw new MappingException("Cannot resolve type information for embedded property " + property, e);
			}
		}
		else {
			typeOfProperty = null;
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
	public boolean isEmbedded() {
		return isEmbedded;
	}

	@Override
	public Class getTypeOfProperty() {
		return typeOfProperty;
	}

	@Override
	public boolean isSpecialType() {
		return this.isSpecialType;
	}

	@Override
	public boolean serializeAsJson() {
		return this.serializeAsJson;
	}
}
