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
package io.awspring.cloud.dynamodb.repository.config;

import io.awspring.cloud.dynamodb.repository.support.DynamoDbRepositoryFactoryBean;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Import;
import org.springframework.data.repository.config.DefaultRepositoryBaseClass;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(DynamoDbRepositoriesRegistrar.class)
public @interface EnableDynamoDbRepositories {

	String[] value() default {};

	String[] basePackages() default {};

	Class<?>[] basePackageClasses() default {};

	Filter[] includeFilters() default {};

	Filter[] excludeFilters() default {};

	String repositoryImplementationPostfix() default "Impl";

	String namedQueriesLocation() default "";

	Class<?> repositoryFactoryBeanClass() default DynamoDbRepositoryFactoryBean.class;

	Class<?> repositoryBaseClass() default DefaultRepositoryBaseClass.class;

	String dynamoDbOperationsRef() default "";

	boolean considerNestedRepositories() default false;
}
