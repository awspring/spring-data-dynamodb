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
package io.awspring.spring.data.dynamodb.repository.config;

import io.awspring.spring.data.dynamodb.repository.support.DynamoDbRepositoryFactoryBean;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Import;
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.data.repository.config.DefaultRepositoryBaseClass;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;

/**
 * Enables scanning and configuration of DynamoDB repositories.
 *
 * @author Matej Nedic
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
@Import(DynamoDbRepositoriesRegistrar.class)
public @interface EnableDynamoDbRepositories {

	/** @return base packages to scan */
	String[] value() default {};

	/** @return base packages to scan */
	String[] basePackages() default {};

	/** @return types whose packages are scanned */
	Class<?>[] basePackageClasses() default {};

	/** @return component filters that include repository interfaces */
	Filter[] includeFilters() default {};

	/** @return component filters that exclude repository interfaces */
	Filter[] excludeFilters() default {};

	/** @return the postfix for custom repository implementations */
	String repositoryImplementationPostfix() default "Impl";

	/** @return the named-query properties resource */
	String namedQueriesLocation() default "";

	/** @return the repository factory-bean type */
	Class<?> repositoryFactoryBeanClass() default DynamoDbRepositoryFactoryBean.class;

	/** @return the repository base-class type */
	Class<?> repositoryBaseClass() default DefaultRepositoryBaseClass.class;

	/** @return the repository query lookup strategy */
	Key queryLookupStrategy() default Key.CREATE_IF_NOT_FOUND;

	/** @return when repositories are initialized */
	BootstrapMode bootstrapMode() default BootstrapMode.DEFAULT;

	/** @return the {@code DynamoDbOperations} bean name */
	String dynamoDbOperationsRef() default "";

	/** @return whether nested repository interfaces are considered */
	boolean considerNestedRepositories() default false;
}
