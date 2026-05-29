package pl.m22.gamehive.user.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;
import pl.m22.gamehive.common.AbstractEntity;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;

import java.time.LocalDate;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile extends AbstractEntity {

    private String firstName;
    private String lastName;
    private String address;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    @URL
    private String profilePictureUrl;

    public void updateFrom(UserProfileUpdateDto dto) {

        if (dto.firstName() != null) this.firstName = dto.firstName();
        if (dto.lastName() != null) this.lastName = dto.lastName();
        if (dto.phoneNumber() != null) this.phoneNumber = dto.phoneNumber();
        if (dto.address() != null) this.address = dto.address();
        if (dto.dateOfBirth() != null) this.dateOfBirth = dto.dateOfBirth();
        if (dto.profilePictureUrl() != null) this.profilePictureUrl = dto.profilePictureUrl();
    }
}
