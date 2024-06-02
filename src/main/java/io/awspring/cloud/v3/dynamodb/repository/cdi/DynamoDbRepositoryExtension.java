package io.awspring.cloud.v3.dynamodb.repository.cdi;

import io.awspring.cloud.v3.dynamodb.core.DynamoDbOperations;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.ProcessBean;
import org.springframework.data.repository.cdi.CdiRepositoryBean;
import org.springframework.data.repository.cdi.CdiRepositoryExtensionSupport;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DynamoDbRepositoryExtension extends CdiRepositoryExtensionSupport {

	private final Map<Set<Annotation>, Bean<DynamoDbOperations>> dynamoDbOperationsMap = new HashMap<>();

	<T> void processBean(@Observes ProcessBean<T> processBean) {

		Bean<T> bean = processBean.getBean();
		bean.getTypes().stream() //
			.filter(type -> type instanceof Class<?> && DynamoDbOperations.class.isAssignableFrom((Class<?>) type)) //
			.forEach(type -> dynamoDbOperationsMap.put(bean.getQualifiers(), ((Bean<DynamoDbOperations>) bean)));
	}

	void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery, BeanManager beanManager) {

		for (Map.Entry<Class<?>, Set<Annotation>> entry : getRepositoryTypes()) {

			Class<?> repositoryType = entry.getKey();
			Set<Annotation> qualifiers = entry.getValue();

			CdiRepositoryBean<?> repositoryBean = createRepositoryBean(repositoryType, qualifiers, beanManager);
			afterBeanDiscovery.addBean(repositoryBean);
			registerBean(repositoryBean);
		}
	}

	private <T> CdiRepositoryBean<T> createRepositoryBean(Class<T> repositoryType, Set<Annotation> qualifiers,
														  BeanManager beanManager) {

		Bean<DynamoDbOperations> dynamoDbOperationsBean = Optional.ofNullable(this.dynamoDbOperationsMap.get(qualifiers))
			.orElseThrow(() -> new UnsatisfiedResolutionException(String.format(
				"Unable to resolve a bean for '%s' with qualifiers %s.", DynamoDbOperations.class.getName(), qualifiers)));

		return new DynamoDbRepositoryBean<>(dynamoDbOperationsBean, qualifiers, repositoryType, beanManager,
			getCustomImplementationDetector());
	}
}
