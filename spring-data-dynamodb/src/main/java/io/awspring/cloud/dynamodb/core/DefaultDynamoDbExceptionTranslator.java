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

import io.awspring.cloud.dynamodb.BadStatementGrammarException;
import io.awspring.cloud.dynamodb.UncategorizedDynamoDbException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.NonTransientDataAccessResourceException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DuplicateItemException;
import software.amazon.awssdk.services.dynamodb.model.ItemCollectionSizeLimitExceededException;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.dynamodb.model.RequestLimitExceededException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TransactionConflictException;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
public class DefaultDynamoDbExceptionTranslator implements DynamoDbExceptionTranslator {

	@Override
	public @Nullable DataAccessException translateExceptionIfPossible(RuntimeException ex) {
		return doTranslate(null, null, ex);
	}

	@Override
	public @Nullable DataAccessException translate(@Nullable String task, @Nullable String statement,
			RuntimeException ex) {
		return doTranslate(task, statement, ex);
	}

	private @Nullable DataAccessException doTranslate(@Nullable String task, @Nullable String statement,
			RuntimeException ex) {

		if (ex instanceof DataAccessException) {
			return (DataAccessException) ex;
		}

		String message = buildMessage(task, statement, ex);

		if (ex instanceof DuplicateItemException) {
			return new DuplicateKeyException(message, ex);
		}

		if (ex instanceof TransactionConflictException || ex instanceof ConditionalCheckFailedException) {
			return new ConcurrencyFailureException(message, ex);
		}

		if (ex instanceof ProvisionedThroughputExceededException
				|| ex instanceof ItemCollectionSizeLimitExceededException) {
			return new TransientDataAccessResourceException(message, ex);
		}

		if (ex instanceof RequestLimitExceededException) {
			return new NonTransientDataAccessResourceException(message, ex);
		}

		if (ex instanceof ResourceNotFoundException) {
			return new BadStatementGrammarException(message, statement, ex);
		}

		if (ex instanceof AwsServiceException) {
			return new UncategorizedDynamoDbException(message, ex);
		}

		return null;
	}

	@SuppressWarnings("NullAway")
	protected String buildMessage(@Nullable String task, @Nullable String statement, RuntimeException ex) {

		if (StringUtils.hasText(task) || StringUtils.hasText(statement)) {
			return task + "; Statement [" + statement + "]; " + ex.getMessage();
		}

		return ex.getMessage();
	}

}
