package pl.m22.gamehive.user.model;

import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import pl.m22.gamehive.common.AbstractEntity;

import java.time.LocalDate;

@Entity
@Table(name = "user_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDetails extends AbstractEntity {

    private String firstName;
    private String lastName;
    private String address;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    @URL
    private String profilePictureUrl;
    @OneToOne(mappedBy = "userDetails")
    private User user;
}
