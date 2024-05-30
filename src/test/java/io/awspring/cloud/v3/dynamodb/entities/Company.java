package io.awspring.cloud.v3.dynamodb.entities;

public class Company {

    private String name;
    private String code;
    private String sortKey4;

    public Company() {
    }

    public Company(String name, String code, String sortKey2) {
        this.name = name;
        this.code = code;
        this.sortKey4 = sortKey2;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSortKey4() {
        return sortKey4;
    }

    public void setSortKey4(String sortKey4) {
        this.sortKey4 = sortKey4;
    }
}
