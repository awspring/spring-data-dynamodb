package io.awspring.cloud.v3.dynamodb.core.mapping.events;

public class DynamoDbBeforeSaveEvent<T> extends DynamoDbMappingEvent{
	public DynamoDbBeforeSaveEvent(T source, String tableName) {
		super(source, tableName);
	}
}
