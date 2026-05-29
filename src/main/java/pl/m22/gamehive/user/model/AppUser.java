package pl.m22.gamehive.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import pl.m22.gamehive.common.AbstractEntity;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "application_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class AppUser extends AbstractEntity {

    @Column(nullable = false, unique = true)
    @NotBlank
    private String username;

    @Column(nullable = false)
    @NotBlank
    @Size(min = 8)
    @JsonIgnore
    private String password;

    @Column(nullable = false, unique = true)
    @Email
    private String email;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id")
    )
    private Set<UserRole> roles = new HashSet<>();

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_profile_id", referencedColumnName = "id")
    private UserProfile userProfile;

    @Column(nullable = false)
    private boolean enabled = false;

    public static AppUser register(String username, String email, String hashedPassword) {

        AppUser appUser = new AppUser();
        appUser.username = username;
        appUser.email = email;
        appUser.password = hashedPassword;
        appUser.enabled = false;
        appUser.userProfile = new UserProfile();

        return appUser;
    }

    public void activate() {

        if (enabled) {
            throw new DomainException(ErrorCode.USER_ALREADY_ACTIVATED, "User is already activated: " + email);
        }

        this.enabled = true;
    }

    public void deactivate() {

        this.enabled = false;
    }

    public void changePassword(String hashedPassword) {

        this.password =  hashedPassword;
    }

    public void assignRole(UserRole role) {

        if (!roles.add(role)) {
            throw new DomainException(ErrorCode.ROLE_ALREADY_ASSIGNED, "Role already assigned: " + role.getName());
        }
    }

    public void replaceRoles(Set<UserRole> newRoles) {

        this.roles = new HashSet<>(newRoles);
    }

    public void attachProfile(UserProfile profile) {

        this.userProfile = profile;
    }
}
