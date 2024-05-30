package io.awspring.cloud.v3.dynamodb.core.mapping;

import org.springframework.data.annotation.Persistent;

import java.lang.annotation.*;

@Documented
@Persistent
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface Table {

	String value() default "";
}
