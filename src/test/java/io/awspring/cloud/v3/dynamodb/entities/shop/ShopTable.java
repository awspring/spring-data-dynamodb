package io.awspring.cloud.v3.dynamodb.entities.shop;

import io.awspring.cloud.v3.dynamodb.core.mapping.InnerClass;
import io.awspring.cloud.v3.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.v3.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.v3.dynamodb.core.mapping.Table;

import java.util.Objects;

@Table(tableName = "shop")
public class ShopTable {
    @PartitionKey
    private String partitionKey;
    @SortKey
    private String sortKey;
    @InnerClass(startsWith = "ORDER")
    private OrderSK order;
    @InnerClass(startsWith = "USER")
    private PersonInformation personInformation;

    public ShopTable() {
    }

    public ShopTable(String partitionKey, String sortKey, OrderSK order, PersonInformation personInformation) {
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
        this.order = order;
        this.personInformation = personInformation;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public OrderSK getOrder() {
        return order;
    }

    public void setOrder(OrderSK order) {
        this.order = order;
    }

    public PersonInformation getPersonInformation() {
        return personInformation;
    }

    public void setPersonInformation(PersonInformation personInformation) {
        this.personInformation = personInformation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShopTable shopTable = (ShopTable) o;
        return Objects.equals(partitionKey, shopTable.partitionKey) && Objects.equals(sortKey, shopTable.sortKey) && Objects.equals(order, shopTable.order) && Objects.equals(personInformation, shopTable.personInformation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(partitionKey, sortKey, order, personInformation);
    }
}
