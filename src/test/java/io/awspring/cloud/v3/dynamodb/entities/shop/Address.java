package io.awspring.cloud.v3.dynamodb.entities.shop;

import java.util.Objects;

public class Address {
    private String city;
    private Long postalCode;
    private String streetAddress;
    private String country;

    public Address() {
    }

    public Address(String city, Long postalCode, String streetAddress, String country) {
        this.city = city;
        this.postalCode = postalCode;
        this.streetAddress = streetAddress;
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Long getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(Long postalCode) {
        this.postalCode = postalCode;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return Objects.equals(city, address.city) && Objects.equals(postalCode, address.postalCode) && Objects.equals(streetAddress, address.streetAddress) && Objects.equals(country, address.country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(city, postalCode, streetAddress, country);
    }
}
