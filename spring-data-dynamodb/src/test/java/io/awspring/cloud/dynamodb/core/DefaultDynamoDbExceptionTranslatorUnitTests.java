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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.awspring.cloud.dynamodb.BadStatementGrammarException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
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

@DisplayName("DefaultDynamoDbExceptionTranslator -- maps AWS SDK exceptions to Spring DataAccessExceptions")
class DefaultDynamoDbExceptionTranslatorUnitTests {

	private final DefaultDynamoDbExceptionTranslator translator = new DefaultDynamoDbExceptionTranslator();

	@Test
	@DisplayName("DuplicateItemException -> DuplicateKeyException (cause preserved)")
	void translate_duplicateItemException_becomesDuplicateKeyException() {
		// Act
		DataAccessException translated = translator
				.translateExceptionIfPossible(DuplicateItemException.builder().message("foo").build());

		// Assert
		assertAll(() -> assertInstanceOf(DuplicateKeyException.class, translated),
				() -> assertInstanceOf(DuplicateItemException.class, translated.getCause()));
	}

	@Test
	@DisplayName("TransactionConflictException -> ConcurrencyFailureException (cause preserved)")
	void translate_transactionConflictException_becomesConcurrencyFailure() {
		DataAccessException translated = translator
				.translateExceptionIfPossible(TransactionConflictException.builder().message("foo").build());

		assertAll(() -> assertInstanceOf(ConcurrencyFailureException.class, translated),
				() -> assertInstanceOf(TransactionConflictException.class, translated.getCause()));
	}

	@Test
	@DisplayName("ConditionalCheckFailedException -> ConcurrencyFailureException (cause preserved)")
	void translate_conditionalCheckFailedException_becomesConcurrencyFailure() {
		DataAccessException translated = translator
				.translateExceptionIfPossible(ConditionalCheckFailedException.builder().message("foo").build());

		assertAll(() -> assertInstanceOf(ConcurrencyFailureException.class, translated),
				() -> assertInstanceOf(ConditionalCheckFailedException.class, translated.getCause()));
	}

	@Test
	@DisplayName("ProvisionedThroughputExceededException -> TransientDataAccessResourceException (cause preserved)")
	void translate_provisionedThroughputExceeded_becomesTransientResource() {
		DataAccessException translated = translator
				.translateExceptionIfPossible(ProvisionedThroughputExceededException.builder().message("foo").build());

		assertAll(() -> assertInstanceOf(TransientDataAccessResourceException.class, translated),
				() -> assertInstanceOf(ProvisionedThroughputExceededException.class, translated.getCause()));
	}

	@Test
	@DisplayName("ItemCollectionSizeLimitExceededException -> TransientDataAccessResourceException (cause preserved)")
	void translate_itemCollectionSizeLimitExceeded_becomesTransientResource() {
		DataAccessException translated = translator.translateExceptionIfPossible(
				ItemCollectionSizeLimitExceededException.builder().message("foo").build());

		assertAll(() -> assertInstanceOf(TransientDataAccessResourceException.class, translated),
				() -> assertInstanceOf(ItemCollectionSizeLimitExceededException.class, translated.getCause()));
	}

	@Test
	@DisplayName("RequestLimitExceededException -> NonTransientDataAccessException (cause preserved)")
	void translate_requestLimitExceeded_becomesNonTransient() {
		DataAccessException translated = translator
				.translateExceptionIfPossible(RequestLimitExceededException.builder().message("foo").build());

		assertAll(() -> assertInstanceOf(NonTransientDataAccessException.class, translated),
				() -> assertInstanceOf(RequestLimitExceededException.class, translated.getCause()));
	}

	@Test
	@DisplayName("ResourceNotFoundException -> BadStatementGrammarException (cause preserved)")
	void translate_resourceNotFound_becomesBadStatementGrammar() {
		DataAccessException translated = translator
				.translateExceptionIfPossible(ResourceNotFoundException.builder().message("foo").build());

		assertAll(() -> assertInstanceOf(BadStatementGrammarException.class, translated),
				() -> assertInstanceOf(ResourceNotFoundException.class, translated.getCause()));
	}

}
