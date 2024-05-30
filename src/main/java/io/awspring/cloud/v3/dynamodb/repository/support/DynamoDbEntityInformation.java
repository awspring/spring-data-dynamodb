package io.awspring.cloud.v3.dynamodb.repository.support;

import org.springframework.data.repository.core.EntityInformation;

public interface DynamoDbEntityInformation <T, ID> extends EntityInformation<T, ID> {

	String getIdAttribute();
	String getTableName();
}
