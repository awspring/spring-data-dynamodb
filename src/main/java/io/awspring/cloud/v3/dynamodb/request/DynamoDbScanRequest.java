package io.awspring.cloud.v3.dynamodb.request;

import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.Map;

public class DynamoDbScanRequest {

    private boolean consistentRead;
    private Map<String, Object> exclusiveStartKey;

    private Map<String, String> expressionAttributeNames;
    private Map<String, Object> expressionAttributeValues;

    private String filterExpression;

    private String indexName;

    private Integer limit;

    private String projectionExpression;

    private Select select;

    public String getFilterExpression() {
        return filterExpression;
    }

    public void setFilterExpression(String filterExpression) {
        this.filterExpression = filterExpression;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public String getProjectionExpression() {
        return projectionExpression;
    }

    public void setProjectionExpression(String projectionExpression) {
        this.projectionExpression = projectionExpression;
    }

    public boolean isConsistentRead() {
        return consistentRead;
    }

    public void setConsistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
    }

    public Map<String, Object> getExclusiveStartKey() {
        return exclusiveStartKey;
    }

    public void setExclusiveStartKey(Map<String, Object> exclusiveStartKey) {
        this.exclusiveStartKey = exclusiveStartKey;
    }

    public Map<String, String> getExpressionAttributeNames() {
        return expressionAttributeNames;
    }

    public void setExpressionAttributeNames(Map<String, String> expressionAttributeNames) {
        this.expressionAttributeNames = expressionAttributeNames;
    }

    public Map<String, Object> getExpressionAttributeValues() {
        return expressionAttributeValues;
    }

    public void setExpressionAttributeValues(Map<String, Object> expressionAttributeValues) {
        this.expressionAttributeValues = expressionAttributeValues;
    }

    public Select getSelect() {
        return select;
    }

    public void setSelect(Select select) {
        this.select = select;
    }


    public static final class Builder {
        private boolean consistentRead;
        private Map<String, Object> exclusiveStartKey;
        private Map<String, String> expressionAttributeNames;
        private Map<String, Object> expressionAttributeValues;
        private String filterExpression;
        private String indexName;
        private Integer limit;
        private String projectionExpression;
        private Select select;

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder withConsistentRead(boolean consistentRead) {
            this.consistentRead = consistentRead;
            return this;
        }

        public Builder withExclusiveStartKey(Map<String, Object> exclusiveStartKey) {
            this.exclusiveStartKey = exclusiveStartKey;
            return this;
        }

        public Builder withExpressionAttributeNames(Map<String, String> expressionAttributeNames) {
            this.expressionAttributeNames = expressionAttributeNames;
            return this;
        }

        public Builder withExpressionAttributeValues(Map<String, Object> expressionAttributeValues) {
            this.expressionAttributeValues = expressionAttributeValues;
            return this;
        }

        public Builder withFilterExpression(String filterExpression) {
            this.filterExpression = filterExpression;
            return this;
        }

        public Builder withIndexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public Builder withLimit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public Builder withProjectionExpression(String projectionExpression) {
            this.projectionExpression = projectionExpression;
            return this;
        }

        public Builder withSelect(Select select) {
            this.select = select;
            return this;
        }

        public DynamoDbScanRequest build() {
            DynamoDbScanRequest dynamoDbScanRequest = new DynamoDbScanRequest();
            dynamoDbScanRequest.setConsistentRead(consistentRead);
            dynamoDbScanRequest.setExclusiveStartKey(exclusiveStartKey);
            dynamoDbScanRequest.setExpressionAttributeNames(expressionAttributeNames);
            dynamoDbScanRequest.setExpressionAttributeValues(expressionAttributeValues);
            dynamoDbScanRequest.setFilterExpression(filterExpression);
            dynamoDbScanRequest.setIndexName(indexName);
            dynamoDbScanRequest.setLimit(limit);
            dynamoDbScanRequest.setProjectionExpression(projectionExpression);
            dynamoDbScanRequest.setSelect(select);
            return dynamoDbScanRequest;
        }
    }
}
