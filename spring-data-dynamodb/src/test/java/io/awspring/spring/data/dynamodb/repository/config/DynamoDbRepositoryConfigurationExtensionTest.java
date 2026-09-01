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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.awspring.spring.data.dynamodb.core.mapping.ItemCollectionView;
import io.awspring.spring.data.dynamodb.core.mapping.SecondaryIndex;
import io.awspring.spring.data.dynamodb.core.mapping.Table;
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepository;
import io.awspring.spring.data.dynamodb.repository.DynamoDbRepositoryFactory;
import io.awspring.spring.data.dynamodb.repository.ItemCollectionRepository;
import io.awspring.spring.data.dynamodb.repository.SecondaryIndexRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;

@DisplayName("DynamoDB repository configuration")
class DynamoDbRepositoryConfigurationExtensionTest {

	@Test
	@DisplayName("all DynamoDB repository models identify the store in strict mode")
	void allDynamoDbRepositoryModelsIdentifyTheStoreInStrictMode() {
		DynamoDbRepositoryConfigurationExtension extension = new DynamoDbRepositoryConfigurationExtension();

		assertTrue(extension.getIdentifyingAnnotations()
				.containsAll(List.of(Table.class, SecondaryIndex.class, ItemCollectionView.class)));
		assertTrue(extension.getIdentifyingTypes().containsAll(
				List.of(DynamoDbRepository.class, SecondaryIndexRepository.class, ItemCollectionRepository.class)));
	}

	@Test
	@DisplayName("enable annotation exposes standard query and bootstrap strategy defaults")
	void enableAnnotationExposesStandardQueryAndBootstrapStrategyDefaults() throws NoSuchMethodException {
		assertEquals(Key.CREATE_IF_NOT_FOUND,
				EnableDynamoDbRepositories.class.getMethod("queryLookupStrategy").getDefaultValue());
		assertEquals(BootstrapMode.DEFAULT,
				EnableDynamoDbRepositories.class.getMethod("bootstrapMode").getDefaultValue());
	}

	@Test
	@DisplayName("spring factories points at the loadable DynamoDB repository factory")
	void springFactoriesPointsAtTheLoadableDynamoDbRepositoryFactory() throws IOException {
		Properties factories = new Properties();
		try (InputStream input = getClass().getClassLoader().getResourceAsStream("META-INF/spring.factories")) {
			assertNotNull(input);
			factories.load(input);
		}

		assertEquals(DynamoDbRepositoryFactory.class.getName(),
				factories.getProperty(RepositoryFactorySupport.class.getName()));
	}
}
