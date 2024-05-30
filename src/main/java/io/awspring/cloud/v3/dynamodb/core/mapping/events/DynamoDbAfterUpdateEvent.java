package io.awspring.cloud.v3.dynamodb.core.mapping.events;

public class DynamoDbAfterUpdateEvent<T> extends DynamoDbMappingEvent{
	public DynamoDbAfterUpdateEvent(T source, String tableName) {
		super(source, tableName);
	}
}
