package io.awspring.cloud.v3.dynamodb.core.mapping.events;

import org.springframework.context.ApplicationEvent;

public class DynamoDbMappingEvent<T> extends ApplicationEvent {
	private String tableName;

	public DynamoDbMappingEvent(T source, String tableName) {
		super(source);
		this.tableName = tableName;
	}

	public String getTableName() {
		return tableName;
	}
}
