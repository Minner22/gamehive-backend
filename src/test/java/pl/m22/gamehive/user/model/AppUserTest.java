package pl.m22.gamehive.user.model;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.HashedPassword;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.common.exception.DomainException;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.m22.gamehive.common.exception.ErrorCode.ROLE_ALREADY_ASSIGNED;
import static pl.m22.gamehive.common.exception.ErrorCode.USER_ALREADY_ACTIVATED;

class AppUserTest {

    private static final PasswordEncoder ENCODER =
            PasswordEncoderFactories.createDelegatingPasswordEncoder();


    @Test
    void register_starts_disabled_with_empty_profile() {

        AppUser appUser = sampleAppUser();

        assertThat(appUser.isEnabled()).isFalse();
    }

    @Test
    void activate_enables_disabled_user() {

        AppUser appUser = sampleAppUser();
        appUser.activate();

        assertThat(appUser.isEnabled()).isTrue();
    }

    @Test
    void activate_on_active_user_throws() {

        AppUser appUser = sampleAppUser();
        appUser.activate();

        assertThatThrownBy(appUser::activate)
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(USER_ALREADY_ACTIVATED);
    }

    @Test
    void changePassword_sets_new_hash() {

        AppUser appUser = sampleAppUser();
        HashedPassword newPw = HashedPassword.fromRaw("newhash123", ENCODER);
        appUser.changePassword(newPw);

        assertThat(appUser.getPassword()).isEqualTo(newPw);
        assertThat(appUser.getPassword().matches("newhash123", ENCODER)).isTrue();
    }


    @Test
    void assignRole_adds_unique_role() {

        AppUser appUser = sampleAppUser();
        UserRole userRole = new UserRole("ROLE_USER", "test role");

        appUser.assignRole(userRole);

        assertThat(appUser.getRoles()).contains(userRole);
    }

    @Test
    void assignRole_duplicate_throws() {

        AppUser appUser = sampleAppUser();
        UserRole userRole = new UserRole("ROLE_USER", "test role");
        userRole.setId(1L);

        appUser.assignRole(userRole);

        assertThatThrownBy(()-> appUser.assignRole(userRole))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(ROLE_ALREADY_ASSIGNED);
    }

    @Test
    void replaceRoles_defensive_copy() {

        AppUser appUser = sampleAppUser();
        UserRole userRole = new UserRole("ROLE_USER", "test user role");
        userRole.setId(1L);
        UserRole adminRole = new UserRole("ROLE_ADMIN", "test admin role");
        adminRole.setId(2L);

        Set<UserRole> mutable = new HashSet<>();
        mutable.add(userRole);
        mutable.add(adminRole);


        appUser.replaceRoles(mutable);

        mutable.clear();

        assertThat(appUser.getRoles())
                .hasSize(2)
                .containsExactlyInAnyOrder(userRole, adminRole);
    }

    private static @NonNull AppUser sampleAppUser() {
        return AppUser.register(
                new Username("testuser"),
                new Email("test@example.org"),
                HashedPassword.fromRaw("Password123", ENCODER));
    }

}
