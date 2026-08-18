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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.util.Lazy;
import org.springframework.data.util.Optionals;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class BasicDynamoDbPersistentProperty extends AnnotationBasedPersistentProperty<DynamoDbPersistentProperty>
		implements DynamoDbPersistentProperty, ApplicationContextAware {
	private NamingStrategy namingStrategy = NamingStrategy.INSTANCE;
	private final Lazy<List<KeyRole>> keyRoles = Lazy.of(this::determineKeyRoles);
	private String columnName;

	private @Nullable StandardEvaluationContext spelContext;

	public BasicDynamoDbPersistentProperty(Property property, PersistentEntity<?, DynamoDbPersistentProperty> owner,
			SimpleTypeHolder simpleTypeHolder) {
		super(property, owner, simpleTypeHolder);
	}

	@Override
	public String getColumnName() {
		if (this.columnName == null) {
			this.columnName = determineColumnName();
		}

		Assert.state(this.columnName != null, () -> String.format("Can't determine column name %s", this));

		return this.columnName;
	}

	private String determineColumnName() {
		Supplier<String> defaultName = () -> getNamingStrategy().getColumnName(this);
		String overriddenName = null;

		if (hasRole(KeyRole.KeyType.PARTITION)) {

			PartitionKey primaryKey = findAnnotation(PartitionKey.class);

			if (primaryKey != null && StringUtils.hasText(primaryKey.value())) {
				overriddenName = primaryKey.value();
			}
			else {
				Column column = findAnnotation(Column.class);
				if (column != null) {
					overriddenName = column.value();
				}
			}

		}
		else if (hasRole(KeyRole.KeyType.SORT)) {
			SortKey sortKey = findAnnotation(SortKey.class);

			if (sortKey != null && StringUtils.hasText(sortKey.value())) {
				overriddenName = sortKey.value();
			}
			else {
				Column column = findAnnotation(Column.class);
				if (column != null) {
					overriddenName = column.value();
				}
			}
		}
		else {

			Column column = findAnnotation(Column.class);
			if (column != null) {
				overriddenName = column.value();
			}
		}

		return createColumnName(defaultName, overriddenName);
	}

	private String createColumnName(Supplier<String> defaultName, String overriddenName) {
		String name;
		if (StringUtils.hasText(overriddenName)) {
			name = overriddenName;
		}
		else {
			name = defaultName.get();
		}
		return name;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.spelContext = new StandardEvaluationContext();
		this.spelContext.addPropertyAccessor(new BeanFactoryAccessor());
		this.spelContext.setBeanResolver(new BeanFactoryResolver(applicationContext));
		this.spelContext.setRootObject(applicationContext);
	}

	NamingStrategy getNamingStrategy() {
		return this.namingStrategy;
	}

	void setNamingStrategy(NamingStrategy namingStrategy) {
		this.namingStrategy = namingStrategy;
	}

	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}

	@Override
	protected Association<DynamoDbPersistentProperty> createAssociation() {
		return new Association<>(this, this);
	}

	@Override
	public AnnotatedType findAnnotatedType(Class<? extends Annotation> annotationType) {

		return Optionals
				.toStream(Optional.ofNullable(getField()).map(Field::getAnnotatedType),
						Optional.ofNullable(getGetter()).map(Method::getAnnotatedReturnType),
						Optional.ofNullable(getSetter()).map(it -> it.getParameters()[0].getAnnotatedType()))
				.filter(it -> AnnotatedElementUtils.hasAnnotation(it, annotationType)).findFirst().orElse(null);
	}

	public boolean isEmbedded() {
		return false;
	}

	@Override
	public boolean isSpecialType() {
		return false;
	}

	@Nullable
	public Class getTypeOfProperty() {
		return null;
	}

	@Override
	@Nullable
	public String startsWith() {
		return null;
	}

	@Override
	@Nullable
	public String endsWith() {
		return null;
	}

	@Override
	@Nullable
	public Pattern regexPattern() {
		return null;
	}

	@Override
	public boolean serializeAsNestedMap() {
		return false;
	}

	@Override
	public boolean isDerived() {
		return isAnnotationPresent(Derived.class);
	}

	@Override
	public boolean isAggregateItem() {
		return isAnnotationPresent(AggregateItem.class);
	}

	@Override
	@Nullable
	public AggregateItem getAggregateItem() {
		return findAnnotation(AggregateItem.class);
	}

	@Override
	public List<KeyRole> getKeyRoles() {
		return keyRoles.get();
	}

	@Override
	public boolean isIdProperty() {
		if (getOwner().isAnnotationPresent(SecondaryIndex.class)) {
			return false;
		}
		return hasRole(KeyRole.KeyType.PARTITION);
	}

	private boolean hasRole(KeyRole.KeyType keyType) {
		for (KeyRole role : getKeyRoles()) {
			if (role.keyType() == keyType) {
				return true;
			}
		}
		return false;
	}

	private List<KeyRole> determineKeyRoles() {
		List<KeyRole> roles = new ArrayList<>();
		AnnotatedElement element = annotatedElementForKeyLookup();
		if (element == null) {
			return List.copyOf(roles);
		}
		for (PartitionKey partitionKey : AnnotatedElementUtils.findMergedRepeatableAnnotations(element,
				PartitionKey.class, PartitionKey.List.class)) {
			roles.add(new KeyRole(KeyRole.KeyType.PARTITION, partitionKey.order()));
		}
		for (SortKey sortKey : AnnotatedElementUtils.findMergedRepeatableAnnotations(element, SortKey.class,
				SortKey.List.class)) {
			roles.add(new KeyRole(KeyRole.KeyType.SORT, sortKey.order()));
		}
		return List.copyOf(roles);
	}

	@Nullable
	private AnnotatedElement annotatedElementForKeyLookup() {
		Field field = getField();
		if (field != null) {
			return field;
		}
		Method getter = getGetter();
		if (getter != null) {
			return getter;
		}
		return null;
	}
}
