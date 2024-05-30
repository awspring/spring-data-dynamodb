package io.awspring.cloud.v3.dynamodb.core.mapping;

import java.lang.annotation.*;

@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = { ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD })
public @interface SortKey {
}
