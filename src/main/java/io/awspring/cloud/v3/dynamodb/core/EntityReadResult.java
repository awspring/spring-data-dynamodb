package io.awspring.cloud.v3.dynamodb.core;

public class EntityReadResult<T> {

	private final T entity;
	private final String nextToken;


	EntityReadResult(T entity, String nextToken) {
		this.entity = entity;
		this.nextToken = nextToken;
	}

	static <T> EntityReadResult<T> of(T entity, String nextToken) {
		return new EntityReadResult<T>(entity, nextToken);
	}

	public T getEntity() {
		return entity;
	}

	public String getNextToken() {
		return nextToken;
	}
}
