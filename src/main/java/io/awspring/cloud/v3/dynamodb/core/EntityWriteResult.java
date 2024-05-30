package io.awspring.cloud.v3.dynamodb.core;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;

public class EntityWriteResult<T>  extends WriteResult{

	private final T entity;


	private EntityWriteResult(T entity,Map<String, AttributeValue> attributes) {
		super(attributes);
		this.entity = entity;
	}


	static <T> EntityWriteResult<T> of(WriteResult result, T entity) {
		return new EntityWriteResult<>(entity, result.getAttributes());
	}


	static <T> EntityWriteResult<T> of(Map<String, AttributeValue> map, T entity) {
		return new EntityWriteResult<T>(entity, map);
	}

	public T getEntity() {
		return entity;
	}
}
