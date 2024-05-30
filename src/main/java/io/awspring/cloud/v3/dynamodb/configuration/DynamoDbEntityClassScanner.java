package io.awspring.cloud.v3.dynamodb.configuration;

import io.awspring.cloud.v3.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.v3.dynamodb.core.mapping.Table;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class DynamoDbEntityClassScanner {

	private Set<String> entityBasePackages = new HashSet<>();

	private Set<Class<?>> entityBasePackageClasses = new HashSet<>();

	private @Nullable
	ClassLoader beanClassLoader;

	public static Set<Class<?>> scan(String... entityBasePackages) throws ClassNotFoundException {
		return new DynamoDbEntityClassScanner(entityBasePackages).scanForEntityClasses();
	}

	public static Set<Class<?>> scan(Class<?>... entityBasePackageClasses) throws ClassNotFoundException {
		return new DynamoDbEntityClassScanner(entityBasePackageClasses).scanForEntityClasses();
	}

	public static Set<Class<?>> scan(Collection<String> entityBasePackages) throws ClassNotFoundException {
		return new DynamoDbEntityClassScanner(entityBasePackages).scanForEntityClasses();
	}

	public DynamoDbEntityClassScanner() {}


	public DynamoDbEntityClassScanner(Class<?>... entityBasePackageClasses) {
		setEntityBasePackageClasses(Arrays.asList(entityBasePackageClasses));
	}

	public DynamoDbEntityClassScanner(String... entityBasePackages) {
		this(Arrays.asList(entityBasePackages));
	}

	public DynamoDbEntityClassScanner(Collection<String> entityBasePackages) {
		setEntityBasePackages(entityBasePackages);
	}

	public DynamoDbEntityClassScanner(Collection<String> entityBasePackages,
									   Collection<Class<?>> entityBasePackageClasses) {

		setEntityBasePackages(entityBasePackages);
		setEntityBasePackageClasses(entityBasePackageClasses);
	}

	public void setEntityBasePackages(Collection<String> entityBasePackages) {
		this.entityBasePackages = new HashSet<>(entityBasePackages);;
	}

	public Set<String> getEntityBasePackages() {
		return entityBasePackages;
	}

	public Set<Class<?>> getEntityBasePackageClasses() {
		return entityBasePackageClasses;
	}

	public void setEntityBasePackageClasses(Collection<Class<?>> entityBasePackageClasses) {
		this.entityBasePackageClasses =  new HashSet<>(entityBasePackageClasses);
	}

	public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}

	public Set<Class<?>> scanForEntityClasses() throws ClassNotFoundException {

		Set<Class<?>> classes = new HashSet<>();

		for (String basePackage : getEntityBasePackages()) {
			classes.addAll(scanBasePackageForEntities(basePackage));
		}

		for (Class<?> basePackageClass : getEntityBasePackageClasses()) {
			classes.addAll(scanBasePackageForEntities(basePackageClass.getPackage().getName()));
		}

		return classes;
	}

	protected Set<Class<?>> scanBasePackageForEntities(String basePackage) throws ClassNotFoundException {

		HashSet<Class<?>> classes = new HashSet<>();

		if (ObjectUtils.isEmpty(basePackage)) {
			return classes;
		}

		ClassPathScanningCandidateComponentProvider componentProvider = new ClassPathScanningCandidateComponentProvider(
			false);

		for (Class<? extends Annotation> annotation : getEntityAnnotations()) {
			componentProvider.addIncludeFilter(new AnnotationTypeFilter(annotation));
		}

		for (BeanDefinition candidate : componentProvider.findCandidateComponents(basePackage)) {

			if (candidate.getBeanClassName() != null) {
				classes.add(ClassUtils.forName(candidate.getBeanClassName(), beanClassLoader));
			}
		}

		return classes;
	}

	@SuppressWarnings("unchecked")
	protected Class<? extends Annotation>[] getEntityAnnotations() {
		return new Class[] { Table.class, PartitionKey.class };
	}
}
