package io.awspring.cloud.v3.dynamodb.entities.shop;

import io.awspring.cloud.v3.dynamodb.core.mapping.InnerClass;
import io.awspring.cloud.v3.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.v3.dynamodb.core.mapping.SortKey;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Order {

    private String username;
    private UUID orderId;
    private Status status;
    private LocalDate createdAt;

    @InnerClass(serializeAsJson = true)
    private Address address;

    public Order() {
    }

    public Order(String username, UUID orderId, Status status, LocalDate createdAt, Address address) {
        this.username = username;
        this.orderId = orderId;
        this.status = status;
        this.createdAt = createdAt;
        this.address = address;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(username, order.username) && Objects.equals(orderId, order.orderId) && Objects.equals(status, order.status) && Objects.equals(createdAt, order.createdAt) && Objects.equals(address, order.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, orderId, status, createdAt, address);
    }
}
