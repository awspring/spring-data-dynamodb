package io.awspring.cloud.v3.dynamodb.core.mapping.events;

public class DynamoDbBeforeDeleteEvent<T> extends DynamoDbMappingEvent {
	public DynamoDbBeforeDeleteEvent(T source, String tableName) {
		super(source, tableName);
	}
}
