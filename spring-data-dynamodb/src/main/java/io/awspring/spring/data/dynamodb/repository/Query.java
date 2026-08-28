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
package io.awspring.spring.data.dynamodb.repository;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.data.annotation.QueryAnnotation;

/**
 * Declares an explicit DynamoDB repository read query.
 *
 * @author Matej Nedic
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@QueryAnnotation
public @interface Query {

	/** @return the DynamoDB key-condition expression */
	String keyConditionExpression() default "";

	/** @return the DynamoDB filter expression */
	String filterExpression() default "";

	/** @return the secondary index name */
	String indexName() default "";

	/** @return whether the query requests a strongly consistent read */
	boolean consistentRead() default false;

	/** @return the maximum evaluated items per request, or {@code -1} for no explicit limit */
	int limit() default -1;

	/** @return whether a scan is allowed when no key condition is available */
	boolean allowScan() default false;

	/** @return the PartiQL statement */
	String partiQl() default "";

	/** @return expression attribute-name mappings */
	ExpressionName[] names() default {};

	/** @return constant expression attribute values */
	ExpressionValue[] values() default {};

}
