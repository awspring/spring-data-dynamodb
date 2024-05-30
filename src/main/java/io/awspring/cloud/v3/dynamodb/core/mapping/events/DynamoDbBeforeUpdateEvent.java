package io.awspring.cloud.v3.dynamodb.core.mapping.events;

public class DynamoDbBeforeUpdateEvent<T>  extends DynamoDbMappingEvent{
	public DynamoDbBeforeUpdateEvent(T source, String tableName) {
		super(source, tableName);
	}
}
