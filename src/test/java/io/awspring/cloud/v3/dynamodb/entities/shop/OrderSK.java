package io.awspring.cloud.v3.dynamodb.entities.shop;

import io.awspring.cloud.v3.dynamodb.core.mapping.Column;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class OrderSK extends Order {

    @Column("GLOBAL_SK_1")
    private String globalSortKey;

    public OrderSK() {
    }

    public OrderSK(String username, UUID orderId, String status, LocalDate createdAt, Address address, String globalSortKey) {
        super(username, orderId, status, createdAt, address);
        this.globalSortKey = globalSortKey;
    }

    public String getGlobalSortKey() {
        return globalSortKey;
    }

    public void setGlobalSortKey(String globalSortKey) {
        this.globalSortKey = globalSortKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OrderSK orderSK = (OrderSK) o;
        return Objects.equals(globalSortKey, orderSK.globalSortKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), globalSortKey);
    }
}
