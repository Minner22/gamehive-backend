package pl.m22.gamehive.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.m22.gamehive.common.domain.PhoneNumber;
import pl.m22.gamehive.common.domain.ProfilePictureUrl;
import pl.m22.gamehive.common.persistence.LongEntity;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;

import java.time.LocalDate;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile extends LongEntity {

    private String firstName;
    private String lastName;

    @Embedded
    @AttributeOverride(name = "street",     column = @Column(name = "street"))
    @AttributeOverride(name = "city",       column = @Column(name = "city"))
    @AttributeOverride(name = "postalCode", column = @Column(name = "postal_code"))
    @AttributeOverride(name = "country",    column = @Column(name = "country"))
    private Address address;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "phone_number"))
    private PhoneNumber phoneNumber;
    private LocalDate dateOfBirth;
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "profile_picture_url"))
    private ProfilePictureUrl profilePictureUrl;

    public void updateFrom(UserProfileUpdateDto dto) {

        if (dto.firstName() != null) this.firstName = dto.firstName();
        if (dto.lastName() != null) this.lastName = dto.lastName();
        if (dto.phoneNumber() != null) this.phoneNumber = new PhoneNumber(dto.phoneNumber());
        if (dto.address() != null) this.address = Address.ofNullable(dto.address().street(),
                dto.address().city(),
                dto.address().postalCode(),
                dto.address().country());
        if (dto.dateOfBirth() != null) this.dateOfBirth = dto.dateOfBirth();
        if (dto.profilePictureUrl() != null) this.profilePictureUrl = new ProfilePictureUrl(dto.profilePictureUrl());
    }
}
