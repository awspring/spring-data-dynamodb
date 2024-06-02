package io.awspring.cloud.v3.dynamodb;

import io.awspring.cloud.v3.dynamodb.core.mapping.Column;
import io.awspring.cloud.v3.dynamodb.core.mapping.InnerClass;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class MyPojo2 {

    @Column("telephoneNumber")
    public String telephoneNumber;
    public Long bill;
    public List<String> ownerFacts;
    public HashMap<String, List<String>> ownerInformations;

    MyPojo2() {
        telephoneNumber = "09";
        bill = 1L;
        ownerFacts = Collections.singletonList("dva");
        ownerInformations = new HashMap<>();
    }


    public HashMap<String, List<String>> getOwnerInformations() {
        return ownerInformations;
    }

    public void setOwnerInformations(HashMap<String, List<String>> ownerInformations) {
        this.ownerInformations = ownerInformations;
    }

    public String getTelephoneNumber() {
        return telephoneNumber;
    }

    public void setTelephoneNumber(String telephoneNumber) {
        this.telephoneNumber = telephoneNumber;
    }

    public Long getBill() {
        return bill;
    }

    public void setBill(Long bill) {
        this.bill = bill;
    }

    public List<String> getOwnerFacts() {
        return ownerFacts;
    }

    public void setOwnerFacts(List<String> ownerFacts) {
        this.ownerFacts = ownerFacts;
    }

}
