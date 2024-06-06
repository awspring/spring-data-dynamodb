package io.awspring.cloud.v3.dynamodb.request;

import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
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
}
