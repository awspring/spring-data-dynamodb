package io.awspring.cloud.v3.dynamodb.core.mapping.events;

public class DynamoDbAfterDeleteEvent<T> extends DynamoDbMappingEvent {
	public DynamoDbAfterDeleteEvent(T source, String tableName) {
		super(source, tableName);
	}
}
