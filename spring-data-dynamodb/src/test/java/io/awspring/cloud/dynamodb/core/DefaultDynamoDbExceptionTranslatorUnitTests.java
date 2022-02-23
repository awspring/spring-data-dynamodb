/*
 * Copyright 2013-2025 the original author or authors.
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
package io.awspring.cloud.dynamodb.core;

import static org.assertj.core.api.Assertions.*;

import io.awspring.cloud.dynamodb.BadStatementGrammarException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DuplicateItemException;
import software.amazon.awssdk.services.dynamodb.model.ItemCollectionSizeLimitExceededException;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.dynamodb.model.RequestLimitExceededException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TransactionConflictException;

class DefaultDynamoDbExceptionTranslatorUnitTests {

	DefaultDynamoDbExceptionTranslator translator = new DefaultDynamoDbExceptionTranslator();

	@Test
	void translatesDuplicateItemException() {

		assertThat(translator.translateExceptionIfPossible(DuplicateItemException.builder().message("foo").build()))
				.isInstanceOf(DuplicateKeyException.class).hasCauseInstanceOf(DuplicateItemException.class);
	}

	@Test
	void translatesTransactionConflictException() {

		assertThat(
				translator.translateExceptionIfPossible(TransactionConflictException.builder().message("foo").build()))
				.isInstanceOf(ConcurrencyFailureException.class).hasCauseInstanceOf(TransactionConflictException.class);
	}

	@Test
	void translatesConditionalCheckFailedException() {

		assertThat(translator
				.translateExceptionIfPossible(ConditionalCheckFailedException.builder().message("foo").build()))
				.isInstanceOf(ConcurrencyFailureException.class)
				.hasCauseInstanceOf(ConditionalCheckFailedException.class);
	}

	@Test
	void translatesProvisionedThroughputExceededException() {

		assertThat(translator
				.translateExceptionIfPossible(ProvisionedThroughputExceededException.builder().message("foo").build()))
				.isInstanceOf(TransientDataAccessResourceException.class)
				.hasCauseInstanceOf(ProvisionedThroughputExceededException.class);
	}

	@Test
	void translatesItemCollectionSizeLimitExceededException() {

		assertThat(translator.translateExceptionIfPossible(
				ItemCollectionSizeLimitExceededException.builder().message("foo").build()))
				.isInstanceOf(TransientDataAccessResourceException.class)
				.hasCauseInstanceOf(ItemCollectionSizeLimitExceededException.class);
	}

	@Test
	void translatesRequestLimitExceededException() {

		assertThat(
				translator.translateExceptionIfPossible(RequestLimitExceededException.builder().message("foo").build()))
				.isInstanceOf(NonTransientDataAccessException.class)
				.hasCauseInstanceOf(RequestLimitExceededException.class);
	}

	@Test
	void translatesResourceNotFoundException() {

		assertThat(translator.translateExceptionIfPossible(ResourceNotFoundException.builder().message("foo").build()))
				.isInstanceOf(BadStatementGrammarException.class).hasCauseInstanceOf(ResourceNotFoundException.class);
	}

}
