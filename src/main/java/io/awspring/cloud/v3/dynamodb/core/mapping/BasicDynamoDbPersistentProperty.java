package io.awspring.cloud.v3.dynamodb.core.mapping;

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
import org.springframework.data.util.Optionals;
import org.springframework.data.util.TypeInformation;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Supplier;

public class BasicDynamoDbPersistentProperty extends AnnotationBasedPersistentProperty<DynamoDbPersistentProperty>
	implements DynamoDbPersistentProperty, ApplicationContextAware {

	private NamingStrategy namingStrategy = NamingStrategy.INSTANCE;

	private String columnName;

	private @Nullable
	StandardEvaluationContext spelContext;


	public BasicDynamoDbPersistentProperty(Property property, PersistentEntity<?, DynamoDbPersistentProperty> owner, SimpleTypeHolder simpleTypeHolder) {
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

	@Override
	public boolean isPartitionKeyColumn() {
		PartitionKey annotation = findAnnotation(PartitionKey.class);

		return annotation != null;
	}

	private String determineColumnName() {
		Supplier<String> defaultName = () -> getNamingStrategy().getColumnName(this);
		String overriddenName = null;

		if (isIdProperty()) {

			PartitionKey primaryKey = findAnnotation(PartitionKey.class);

			if (primaryKey != null) {
				overriddenName = primaryKey.value();
			}

		} else {

			Column column = findAnnotation(Column.class);
			if (column != null) {
				overriddenName = column.value();
			} else {
				var sortKey = findAnnotation(SortKey.class);
				if (sortKey != null) {
					overriddenName = sortKey.value();
				}
			}
		}

		return createColumnName(defaultName, overriddenName);
	}

	private String createColumnName(Supplier<String> defaultName, String overriddenName) {
		String name;
		if (StringUtils.hasText(overriddenName)) {
			name = overriddenName;
		} else {
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
			.filter(it -> hasAnnotation(it, annotationType, getTypeInformation())).findFirst().orElse(null);
	}
	public boolean isEmbedded() {
		return false;
	}

	@Override
	public boolean isSpecialType() {
		return false;
	}

	public Class getTypeOfProperty() {
		return null;
	}

	@Override
	public String startsWith() {
		return null;
	}

	@Override
	public String endsWith() {
		return null;
	}


	@Override
	public boolean serializeAsJson() {
		return false;
	}

	@Override
	public boolean isRangeKey() {
		SortKey annotation = findAnnotation(SortKey.class);

		return annotation != null;
	}

	private static boolean hasAnnotation(AnnotatedType type, Class<? extends Annotation> annotationType,
										 TypeInformation<?> typeInformation) {

		if (AnnotatedElementUtils.hasAnnotation(type, annotationType)) {
			return true;
		}
		return false;
	}
}
