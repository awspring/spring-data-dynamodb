package io.awspring.cloud.v3.dynamodb.repository;

import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

@NoRepositoryBean
public interface DynamoDbRepository<T, KEY> {

	<S extends T> S save(S entity);

	/**
	 * Saves all given entities.
	 *
	 * @param entities must not be {@literal null} nor must it contain {@literal null}.
	 * @return the saved entities; will never be {@literal null}. The returned {@literal Iterable} will have the same size
	 *         as the {@literal Iterable} passed as an argument.
	 * @throws IllegalArgumentException in case the given {@link Iterable entities} or one of its entities is
	 *           {@literal null}.
	 */
	<S extends T> Iterable<S> saveAll(Iterable<S> entities);

	/**
	 * Retrieves an entity by its KEY.
	 *
	 * @param key must not be {@literal null}.
	 * @return the entity with the given KEY or {@literal Optional#empty()} if none found.
	 * @throws IllegalArgumentException if {@literal KEY} is {@literal null}.
	 */
	Optional<T> findByPartitionKey(KEY key);

	/**
	 * Deletes a given entity.
	 *
	 * @param entity must not be {@literal null}.
	 * @throws IllegalArgumentException in case the given entity is {@literal null}.
	 */
	void delete(T entity);
	

	<S extends T> S update(S entity);
}
