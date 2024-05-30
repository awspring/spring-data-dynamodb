package io.awspring.cloud.v3.dynamodb.core.mapping.events;

public class DynamoDbAfterSaveEvent<T> extends DynamoDbMappingEvent{
	public DynamoDbAfterSaveEvent(T source, String tableName) {
		super(source, tableName);
	}
}
