package io.awspring.cloud.v3.dynamodb.core.mapping;

import org.springframework.data.annotation.Id;

import java.lang.annotation.*;

@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = { ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD })
@Id
public @interface PartitionKey {

	String value() default "";
}
