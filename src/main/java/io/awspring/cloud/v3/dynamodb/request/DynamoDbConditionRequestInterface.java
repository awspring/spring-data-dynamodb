package io.awspring.cloud.v3.dynamodb.request;

@FunctionalInterface
public interface DynamoDbConditionRequestInterface {

    DynamoDbConditionRequest build(DynamoDbConditionRequest.Builder builder);
}
