package io.awspring.cloud.v3.dynamodb.core;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public class WriteResult{

	private final Map<String, AttributeValue> attributes;

	WriteResult(Map<String, AttributeValue> attributes) {
		this.attributes = attributes;
	}


	public Map<String, AttributeValue> getAttributes() {
		return attributes;
	}
}
