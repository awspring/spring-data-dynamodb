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
package io.awspring.cloud.dynamodb.core.mapping;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.stream.Collectors;
import org.springframework.data.mapping.MappingException;
import org.springframework.util.Assert;

/**
 * @author Matej Nedic
 * @since 1.0.0
 */
@SuppressWarnings("serial")
public class VerifierMappingExceptions extends MappingException {

	private final Collection<MappingException> exceptions;

	private final String className;

	public VerifierMappingExceptions(DynamoDbPersistentEntity<?> entity, Collection<MappingException> exceptions) {

		super(String.format("Mapping Exceptions for %s", entity.getName()));

		Assert.notNull(entity, "DynamoDbPersistentEntity must not be null");

		this.exceptions = Collections.unmodifiableCollection(new LinkedList<>(exceptions));
		this.className = entity.getType().getName();

		this.exceptions.forEach(this::addSuppressed);
	}

	public VerifierMappingExceptions(DynamoDbPersistentEntity<?> entity, String message) {

		super(message);

		Assert.notNull(entity, "DynamoDbPersistentEntity must not be null");

		this.exceptions = Collections.emptyList();
		this.className = entity.getType().getName();
	}

	public Collection<MappingException> getMappingExceptions() {
		return exceptions;
	}

	public Collection<String> getMessages() {
		return exceptions.stream().map(Throwable::getMessage).collect(Collectors.toList());
	}

	@Override
	public String getMessage() {

		StringBuilder builder = new StringBuilder(className).append(":\n");

		exceptions.forEach(e -> builder.append(" - ").append(e.getMessage()).append("\n"));

		return builder.toString();
	}

}
