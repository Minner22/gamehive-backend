package pl.m22.gamehive.user.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    //bez walidacji póki co, to będzie dodane w dalszych etapach rozwoju
    private String street;
    private String city;
    private String postalCode;
    private String country;

    public boolean isEmpty() {

        return (street == null || street.isBlank()) &&
               (city == null || city.isBlank()) &&
               (postalCode == null || postalCode.isBlank()) &&
               (country == null || country.isBlank());
    }

    public static Address ofNullable(String street, String city, String postalCode, String country) {

        Address address = new Address(street, city, postalCode, country);
        return address.isEmpty() ? null : address;
    }
}
