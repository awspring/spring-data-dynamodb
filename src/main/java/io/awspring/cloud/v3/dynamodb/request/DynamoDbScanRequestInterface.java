package io.awspring.cloud.v3.dynamodb.request;

@FunctionalInterface
public interface DynamoDbScanRequestInterface {

    DynamoDbScanRequest build(DynamoDbScanRequest.Builder builder);
}
