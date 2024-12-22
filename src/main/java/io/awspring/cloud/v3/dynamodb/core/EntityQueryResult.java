package io.awspring.cloud.v3.dynamodb.core;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public class EntityQueryResult<T> {

    private final T entity;
    private final Integer count;

    private final Map<String, Object> lastEvaluatedKey;

    private EntityQueryResult(T entity, Integer count) {
      this(entity, count, null);
    }

    private EntityQueryResult(T entity, Integer count, Map<String, Object> lastEvaluatedKey) {
        this.entity = entity;
        this.count = count;
        this.lastEvaluatedKey = lastEvaluatedKey;
    }



    static <T> EntityQueryResult<T> of(T entity) {
        return new EntityQueryResult<T>(entity, null);
    }

    static <T> EntityQueryResult<T> of(T entity, Integer count) {
        return new EntityQueryResult<T>(entity, count);
    }

    static <T> EntityQueryResult<T> of(T entity, Integer count, Map<String, Object> lastEvaluatedKey) {
        return new EntityQueryResult<T>(entity, count, lastEvaluatedKey);
    }

    public T getEntity() {
        return entity;
    }

    public Integer getCount() {
        return count;
    }

    public Map<String, Object> getLastEvaluatedKey() {
        return lastEvaluatedKey;
    }
}
