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
package io.awspring.cloud.dynamodb.repository;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.data.annotation.QueryAnnotation;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@QueryAnnotation
public @interface Query {

	String keyConditionExpression() default "";

	String filterExpression() default "";

	String indexName() default "";

	boolean consistentRead() default false;

	int limit() default -1;

	boolean allowScan() default false;

	boolean typeFilter() default true;

	String partiQl() default "";

	String updateExpression() default "";

	String conditionExpression() default "";

	ExpressionName[] names() default {};

	ExpressionValue[] values() default {};

}
