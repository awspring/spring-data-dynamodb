package io.awspring.cloud.v3.dynamodb.entities;

import io.awspring.cloud.v3.dynamodb.core.mapping.InnerClass;
import io.awspring.cloud.v3.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.v3.dynamodb.core.mapping.SortKey;
import io.awspring.cloud.v3.dynamodb.core.mapping.Table;

@Table("company_table")
public class CompanySingleTable {
    @PartitionKey
    private String partitionKey;
    @SortKey
    private String sortKey;

    @InnerClass
    private Company company;

    @InnerClass
    private Person person;

    public CompanySingleTable() {
    }

    public CompanySingleTable(String partitionKey, String sortKey, Company company, Person person) {
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
        this.company = company;
        this.person = person;
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

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }
}
