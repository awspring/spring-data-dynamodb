package io.awspring.cloud.v3.dynamodb.request;

@FunctionalInterface
public interface DynamoDbUpdateExpressionRequestInterface {

    DynamoDbUpdateExpressionRequest build(DynamoDbUpdateExpressionRequest.Builder builder);
}
