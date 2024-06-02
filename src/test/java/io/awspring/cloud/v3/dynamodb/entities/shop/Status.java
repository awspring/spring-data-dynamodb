package io.awspring.cloud.v3.dynamodb.entities.shop;

public enum Status {

    SHIPPED("SHIPPED"), DELIVERED("DELIVIERED"), IN_PROGRESS("IN_PROGRESS");

    private final String status;

    Status(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
