package io.awspring.cloud.v3.dynamodb.core.mapping.events;

import org.springframework.data.mapping.callback.EntityCallback;

@FunctionalInterface
public interface DynamoDbBeforeSaveCallback<T> extends EntityCallback<T> {

	T onBeforeSave(T entity, String tableName);
}
