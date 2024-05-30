package io.awspring.cloud.v3.dynamodb.entities.shop;

import io.awspring.cloud.v3.dynamodb.core.mapping.InnerClass;

import java.time.LocalDate;
import java.util.Objects;

public class PersonInformation {
    private String userName;
    private String fullName;
    private String email;
    private LocalDate createdAt;
    @InnerClass(serializeAsJson = true)
    private Address address;

    public PersonInformation() {
    }

    public PersonInformation(String userName, String fullName, String email, LocalDate createdAt, Address address) {
        this.userName = userName;
        this.fullName = fullName;
        this.email = email;
        this.createdAt = createdAt;
        this.address = address;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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
        PersonInformation that = (PersonInformation) o;
        return Objects.equals(userName, that.userName) && Objects.equals(fullName, that.fullName) && Objects.equals(email, that.email) && Objects.equals(createdAt, that.createdAt) && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName, fullName, email, createdAt, address);
    }
}
