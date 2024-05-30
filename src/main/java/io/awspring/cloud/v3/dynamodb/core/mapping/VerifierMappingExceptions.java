package io.awspring.cloud.v3.dynamodb.core.mapping;

import org.springframework.data.mapping.MappingException;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.stream.Collectors;

/**
 * Aggregator of multiple {@link MappingException} for convenience when verifying persistent entities. This allows the
 * framework to communicate all verification errors to the user of the framework, rather than one at a time.
 *
 * @author David Webb
 * @author Mark Paluch
 * @author Matej Nedic
 */
@SuppressWarnings("serial")
public class VerifierMappingExceptions extends MappingException {

	private final Collection<MappingException> exceptions;

	private final String className;

	public VerifierMappingExceptions(DynamoDbPersistenceEntity<?> entity, Collection<MappingException> exceptions) {

		super(String.format("Mapping Exceptions for %s", entity.getName()));

		Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");

		this.exceptions = Collections.unmodifiableCollection(new LinkedList<>(exceptions));
		this.className = entity.getType().getName();

		this.exceptions.forEach(this::addSuppressed);
	}

	/**
	 * Create a new {@link VerifierMappingExceptions} for the given {@code entity} and message.
	 *
	 * @param entity must not be {@literal null}.
	 * @param message
	 */
	public VerifierMappingExceptions(DynamoDbPersistenceEntity<?> entity, String message) {

		super(message);

		Assert.notNull(entity, "DynamoDbPersistenceEntity must not be null");

		this.exceptions = Collections.emptyList();
		this.className = entity.getType().getName();
	}

	/**
	 * Returns a list of the {@link MappingException}s aggregated within.
	 *
	 * @return collection of {@link MappingException}.
	 */
	public Collection<MappingException> getMappingExceptions() {
		return exceptions;
	}

	/**
	 * Returns a list of the {@link MappingException} messages aggregated within.
	 *
	 * @return collection of messages.
	 */
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
