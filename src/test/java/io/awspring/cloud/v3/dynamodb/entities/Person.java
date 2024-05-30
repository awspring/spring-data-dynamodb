package io.awspring.cloud.v3.dynamodb.entities;

import java.time.LocalDate;

public class Person {

    private String firstName;
    private String lastName;
    private LocalDate age;
    private String sortKey2;

    public Person() {
    }

    public Person(String firstName, String lastName, LocalDate age, String sortKey2) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
        this.sortKey2 = sortKey2;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getAge() {
        return age;
    }

    public void setAge(LocalDate age) {
        this.age = age;
    }

    public String getSortKey2() {
        return sortKey2;
    }

    public void setSortKey2(String sortKey2) {
        this.sortKey2 = sortKey2;
    }
}
