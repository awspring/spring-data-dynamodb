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

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.model.BasicPersistentEntity;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class BasicDynamoDbPersistentEntity<T> extends BasicPersistentEntity<T, DynamoDbPersistentProperty>
		implements DynamoDbPersistentEntity<T>, ApplicationContextAware {

	private static final DynamoDbPersistentEntityMetadataVerifier DEFAULT_VERIFIER = new CompositeDynamoDbPersistentEntityMetadataVerifier();

	private @Nullable StandardEvaluationContext spelContext;
	private DynamoDbPersistentEntityMetadataVerifier verifier = DEFAULT_VERIFIER;

	private @Nullable String tableName;

	private NamingStrategy namingStrategy = NamingStrategy.INSTANCE;

	private @Nullable DynamoDbMappingContext mappingContext;

	private final IndexKeySchemaBuilder localSchemaBuilder = new IndexKeySchemaBuilder("");

	public BasicDynamoDbPersistentEntity(TypeInformation<T> typeInformation,
			DynamoDbPersistentEntityMetadataVerifier verifier) {

		super(typeInformation, DynamoDbPersistentPropertyComparator.INSTANCE);

		setVerifier(verifier);
	}

	public BasicDynamoDbPersistentEntity(TypeInformation<T> information,
			Comparator<DynamoDbPersistentProperty> comparator) {
		super(information, comparator);
	}

	private String determineTableName() {
		Table annotation = findAnnotation(Table.class);

		if (annotation != null && StringUtils.hasText(annotation.tableName())) {
			return annotation.tableName();
		}
		return namingStrategy.getTableName(this);
	}

	private String resolveViewTableName() {
		SecondaryIndex annotation = findAnnotation(SecondaryIndex.class);
		if (annotation != null && StringUtils.hasText(annotation.tableName())) {
			return annotation.tableName();
		}
		Set<String> candidates = this.mappingContext != null ? this.mappingContext.distinctBaseTableNames()
				: Collections.emptySet();
		if (candidates.size() == 1) {
			return candidates.iterator().next();
		}
		throw new IllegalStateException(String.format(
				"@SecondaryIndex view %s cannot resolve its physical table: expected exactly one @Table entity "
						+ "table name but found %s. Set @SecondaryIndex(tableName=...) explicitly.",
				getType().getName(), candidates.isEmpty() ? "none" : candidates));
	}

	public DynamoDbPersistentEntityMetadataVerifier getVerifier() {
		return verifier;
	}

	public void setVerifier(DynamoDbPersistentEntityMetadataVerifier verifier) {
		this.verifier = verifier;
	}

	void setMappingContext(DynamoDbMappingContext mappingContext) {
		this.mappingContext = mappingContext;
	}

	@Override
	public void verify() throws MappingException {

		super.verify();

		this.verifier.verify(this);

		if (this.tableName == null && !isSecondaryIndexView()) {
			setTableName(determineTableName());
		}
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	@Override
	public String getTableName() {
		if (this.tableName != null) {
			return this.tableName;
		}
		String resolved = isSecondaryIndexView() ? resolveViewTableName() : determineTableName();
		this.tableName = resolved;
		return resolved;
	}

	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		Assert.notNull(context, "ApplicationContext must not be null");

		spelContext = new StandardEvaluationContext();
		spelContext.addPropertyAccessor(new BeanFactoryAccessor());
		spelContext.setBeanResolver(new BeanFactoryResolver(context));
		spelContext.setRootObject(context);
	}

	public NamingStrategy getNamingStrategy() {
		return namingStrategy;
	}

	public void setNamingStrategy(NamingStrategy namingStrategy) {
		this.namingStrategy = namingStrategy;
	}

	@Override
	public void addPersistentProperty(DynamoDbPersistentProperty property) {
		super.addPersistentProperty(property);
		for (KeyRole role : property.getKeyRoles()) {
			localSchemaBuilder.add(role, property);
		}
	}

	@Override
	public IndexKeySchema getKeySchema() {
		return localSchemaBuilder.build();
	}

	@Override
	public String getTypeName() {
		Table annotation = findAnnotation(Table.class);
		if (annotation != null && StringUtils.hasText(annotation.typeName())) {
			return annotation.typeName();
		}
		return getType().getSimpleName();
	}

	@Override
	public String getDiscriminatorColumn() {
		Table annotation = findAnnotation(Table.class);
		return annotation != null ? annotation.discriminator() : "";
	}

	@Override
	public boolean isSecondaryIndexView() {
		return findAnnotation(SecondaryIndex.class) != null;
	}

	@Override
	@Nullable
	public String getIndexName() {
		SecondaryIndex annotation = findAnnotation(SecondaryIndex.class);
		if (annotation == null) {
			return null;
		}
		return StringUtils.hasText(annotation.name()) ? annotation.name() : annotation.value();
	}
}
