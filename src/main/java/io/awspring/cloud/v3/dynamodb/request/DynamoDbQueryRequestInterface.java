package io.awspring.cloud.v3.dynamodb.request;

@FunctionalInterface
public interface DynamoDbQueryRequestInterface {

    DynamoDbQueryRequest build(DynamoDbQueryRequest.Builder builder);
}
