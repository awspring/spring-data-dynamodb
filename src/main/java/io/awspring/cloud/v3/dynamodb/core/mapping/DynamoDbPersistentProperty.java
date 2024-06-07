package io.awspring.cloud.v3.dynamodb.core.mapping;

import org.springframework.context.ApplicationContextAware;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.lang.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;

public interface DynamoDbPersistentProperty	extends PersistentProperty<DynamoDbPersistentProperty>, ApplicationContextAware {

	/**
	 * The name of the single column to which the property is persisted.
	 */
	@Nullable
	String getColumnName();

	/**
	 * Whether the property is a partition key column.
	 */
	boolean isPartitionKeyColumn();

	@Nullable
	AnnotatedType findAnnotatedType(Class<? extends Annotation> annotationType);


    boolean isRangeKey();

	 boolean isEmbedded();

	 boolean isSpecialType();

	 Class getTypeOfProperty();

	 String startsWith();

	 String endsWith();

	 boolean serializeAsJson();
}
