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
package io.awspring.spring.data.dynamodb.core.mapping;

import java.lang.annotation.*;
import org.springframework.data.annotation.Id;

/**
 * Marks a property as a partition-key component.
 *
 * @author Matej Nedic
 * @since 1.0.0
 */
@Documented
@Id
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = { ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD })
@Repeatable(PartitionKey.List.class)
public @interface PartitionKey {

	/**
	 * @return the physical attribute name, or empty to use the mapped property name
	 */
	String value() default "";

	/**
	 * @return the zero-based position in a composite partition key
	 */
	int order() default 0;

	/**
	 * Holds repeated partition-key declarations.
	 */
	@Documented
	@Retention(value = RetentionPolicy.RUNTIME)
	@Target(value = { ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD })
	@interface List {

		/**
		 * @return the partition-key declarations
		 */
		PartitionKey[] value();
	}
}
