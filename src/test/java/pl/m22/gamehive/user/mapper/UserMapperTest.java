package pl.m22.gamehive.user.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.user.dto.UserProfileResponseDto;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.dto.UserResponseDto;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserProfile;
import pl.m22.gamehive.user.model.UserRole;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("toUserResponseDto() -> mapuje AppUser na UserResponseDto z rolami i profilem")
    void toUserResponseDto_maps_all_fields() {
        UserRole role = new UserRole();
        role.setName("ROLE_USER");

        UserProfile profile = new UserProfile();
        profile.setFirstName("Jan");
        profile.setLastName("Kowalski");
        profile.setPhoneNumber("+48123456789");
        profile.setAddress("Warszawa");
        profile.setDateOfBirth(LocalDate.of(1990, 1, 15));
        profile.setProfilePictureUrl("https://example.com/avatar.png");

        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("jan_kowalski");
        user.setEmail("jan@example.com");
        user.setPassword("secret123");
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        user.setUserProfile(profile);

        UserResponseDto result = userMapper.toUserResponseDto(user);

        assertEquals(1L, result.id());
        assertEquals("jan_kowalski", result.username());
        assertEquals("jan@example.com", result.email());
        assertTrue(result.enabled());
        assertEquals(Set.of("ROLE_USER"), result.roles());
        assertNotNull(result.profile());
        assertEquals("Jan", result.profile().firstName());
        assertEquals("Kowalski", result.profile().lastName());
    }

    @Test
    @DisplayName("toUserResponseDto() -> null profile -> profile jest null w DTO")
    void toUserResponseDto_null_profile() {
        UserRole role = new UserRole();
        role.setName("ROLE_USER");

        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("test");
        user.setEmail("test@example.com");
        user.setPassword("secret123");
        user.setEnabled(false);
        user.setRoles(Set.of(role));
        user.setUserProfile(null);

        UserResponseDto result = userMapper.toUserResponseDto(user);

        assertNull(result.profile());
        assertEquals(Set.of("ROLE_USER"), result.roles());
    }

    @Test
    @DisplayName("toUserProfileResponseDto() -> mapuje UserProfile na UserProfileResponseDto")
    void toUserProfileResponseDto_maps_all_fields() {
        UserProfile profile = new UserProfile();
        profile.setFirstName("Anna");
        profile.setLastName("Nowak");
        profile.setPhoneNumber("+48987654321");
        profile.setAddress("Krakow");
        profile.setDateOfBirth(LocalDate.of(1985, 6, 20));
        profile.setProfilePictureUrl("https://example.com/photo.jpg");

        UserProfileResponseDto result = userMapper.toUserProfileResponseDto(profile);

        assertEquals("Anna", result.firstName());
        assertEquals("Nowak", result.lastName());
        assertEquals("+48987654321", result.phoneNumber());
        assertEquals("Krakow", result.address());
        assertEquals(LocalDate.of(1985, 6, 20), result.dateOfBirth());
        assertEquals("https://example.com/photo.jpg", result.profilePictureUrl());
    }

    @Test
    @DisplayName("updateUserProfileFromDto() -> aktualizuje tylko nie-null pola")
    void updateUserProfileFromDto_ignores_null_fields() {
        UserProfile profile = new UserProfile();
        profile.setFirstName("Jan");
        profile.setLastName("Kowalski");
        profile.setPhoneNumber("+48111222333");
        profile.setAddress("Warszawa");
        profile.setDateOfBirth(LocalDate.of(1990, 1, 1));
        profile.setProfilePictureUrl("https://example.com/old.png");

        UserProfileUpdateDto dto = new UserProfileUpdateDto(
                "Janusz", null, null, "Gdansk", null, null
        );

        userMapper.updateUserProfileFromDto(dto, profile);

        assertEquals("Janusz", profile.getFirstName());
        assertEquals("Kowalski", profile.getLastName());
        assertEquals("+48111222333", profile.getPhoneNumber());
        assertEquals("Gdansk", profile.getAddress());
        assertEquals(LocalDate.of(1990, 1, 1), profile.getDateOfBirth());
        assertEquals("https://example.com/old.png", profile.getProfilePictureUrl());
    }

    @Test
    @DisplayName("updateUserProfileFromDto() -> aktualizuje wszystkie pola")
    void updateUserProfileFromDto_updates_all_fields() {
        UserProfile profile = new UserProfile();
        profile.setFirstName("Jan");
        profile.setLastName("Kowalski");

        UserProfileUpdateDto dto = new UserProfileUpdateDto(
                "Anna", "Nowak", "+48999888777", "Poznan",
                LocalDate.of(1995, 3, 10), "https://example.com/new.png"
        );

        userMapper.updateUserProfileFromDto(dto, profile);

        assertEquals("Anna", profile.getFirstName());
        assertEquals("Nowak", profile.getLastName());
        assertEquals("+48999888777", profile.getPhoneNumber());
        assertEquals("Poznan", profile.getAddress());
        assertEquals(LocalDate.of(1995, 3, 10), profile.getDateOfBirth());
        assertEquals("https://example.com/new.png", profile.getProfilePictureUrl());
    }

    @Test
    @DisplayName("toCredentialsDto() -> mapuje AppUser na CredentialsDto z rolami jako String")
    void toCredentialsDto_maps_email_and_roles() {
        UserRole role = new UserRole();
        role.setName("ROLE_ADMIN");

        AppUser user = new AppUser();
        user.setEmail("admin@example.com");
        user.setRoles(Set.of(role));

        CredentialsDto result = userMapper.toCredentialsDto(user);

        assertEquals("admin@example.com", result.email());
        assertEquals(Set.of("ROLE_ADMIN"), result.roles());
    }

    @Test
    @DisplayName("toUser(RegistrationDto) -> mapuje RegistrationDto na AppUser")
    void toUser_from_registrationDto() {
        RegistrationDto dto = new RegistrationDto("newuser", "new@example.com", "password123");

        AppUser result = userMapper.toUser(dto);

        assertEquals("newuser", result.getUsername());
        assertEquals("new@example.com", result.getEmail());
        assertEquals("password123", result.getPassword());
    }
}