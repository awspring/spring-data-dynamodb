package io.awspring.cloud.v3.dynamodb.core.mapping;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value = { ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
public @interface Column {

	String value() default "";

	boolean isStatic() default false;
}
