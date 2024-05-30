package io.awspring.cloud.v3.dynamodb.core.mapping;

import java.lang.annotation.*;

@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface InnerClass {

    String regex() default "";

    boolean serializeAsJson() default false;
}
