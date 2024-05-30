package io.awspring.cloud.v3.dynamodb.core;

public class EntityQueryResult<T> {

    private final T entity;
    private final Long count;

    private EntityQueryResult(T entity, Long count) {
        this.entity = entity;
        this.count = count;
    }


    static <T> EntityQueryResult<T> of(T entity) {
        return new EntityQueryResult<T>(entity, null);
    }

    static <T> EntityQueryResult<T> of(T entity, Long count) {
        return new EntityQueryResult<T>(entity, count);
    }

    public T getEntity() {
        return entity;
    }
}
