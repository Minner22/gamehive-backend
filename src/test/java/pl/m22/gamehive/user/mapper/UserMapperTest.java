package pl.m22.gamehive.user.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.common.domain.*;
import pl.m22.gamehive.support.SeededUsers;
import pl.m22.gamehive.user.dto.AddressDto;
import pl.m22.gamehive.user.dto.UserProfileResponseDto;
import pl.m22.gamehive.user.dto.UserResponseDto;
import pl.m22.gamehive.user.model.Address;
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

        UserRole role = new UserRole("ROLE_USER", null);

        UserProfile profile = new UserProfile(
                "Jan", "Kowalski",
                new Address("ul. Testowa 1", "Warszawa", "00-001", "Polska"),
                new PhoneNumber("+48123456789"),
                LocalDate.of(1990, 1, 15), new ProfilePictureUrl("https://example.com/avatar.png")
        );

        AppUser user = AppUser.register(new Username("jan_kowalski"), new Email("jan@example.com"), HashedPassword.fromHash("{noop}secret123"));
        user.setId(SeededUsers.JOHN_ID);
        user.activate();
        user.assignRole(role);
        user.attachProfile(profile);

        UserResponseDto result = userMapper.toUserResponseDto(user);

        assertEquals(SeededUsers.JOHN_ID, result.id());
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

        UserRole role = new UserRole("ROLE_USER", null);

        AppUser user = AppUser.register(new Username("test"), new Email("test@example.com"), HashedPassword.fromHash("{noop}secret123"));
        user.setId(SeededUsers.JOHN_ID);
        user.activate();
        user.assignRole(role);
        user.attachProfile(null);

        UserResponseDto result = userMapper.toUserResponseDto(user);

        assertNull(result.profile());
        assertEquals(Set.of("ROLE_USER"), result.roles());
    }

    @Test
    @DisplayName("toUserProfileResponseDto() -> mapuje UserProfile na UserProfileResponseDto")
    void toUserProfileResponseDto_maps_all_fields() {

        UserProfile profile = new UserProfile(
                "Anna", "Nowak",
                new Address("ul. Rynek 5", "Krakow", "30-001", "Polska"),
                new PhoneNumber("+48987654321"),
                LocalDate.of(1985, 6, 20), new ProfilePictureUrl("https://example.com/photo.jpg")
        );

        UserProfileResponseDto result = userMapper.toUserProfileResponseDto(profile);

        assertEquals("Anna", result.firstName());
        assertEquals("Nowak", result.lastName());
        assertEquals("+48987654321", result.phoneNumber());
        assertNotNull(result.address());
        assertEquals("ul. Rynek 5", result.address().street());
        assertEquals("Krakow", result.address().city());
        assertEquals("30-001", result.address().postalCode());
        assertEquals("Polska", result.address().country());
        assertEquals(LocalDate.of(1985, 6, 20), result.dateOfBirth());
        assertEquals("https://example.com/photo.jpg", result.profilePictureUrl());
    }

    @Test
    @DisplayName("toCredentialsDto() -> mapuje AppUser na CredentialsDto z rolami jako String")
    void toCredentialsDto_maps_email_and_roles() {

        UserRole role = new UserRole("ROLE_ADMIN", null);

        AppUser user = AppUser.register(new Username("admin"), new Email("admin@example.com"), HashedPassword.fromHash("{noop}password"));

        user.assignRole(role);

        CredentialsDto result = userMapper.toCredentialsDto(user);

        assertEquals("admin@example.com", result.email());
        assertEquals(Set.of("ROLE_ADMIN"), result.roles());
    }

    @Test
    @DisplayName("toUserProfileResponseDto() -> Address ze wszystkimi polami null -> address w DTO null")
    void toUserProfileResponseDto_emptyAddress_mapsToNull() {

        UserProfile profile = new UserProfile(
                "Jan", "Kowalski", new Address(null, null, null, null),
                new PhoneNumber("+48123456789"),
                LocalDate.of(1990, 1, 15), new ProfilePictureUrl("https://example.com/a.png"));

        assertNull(userMapper.toUserProfileResponseDto(profile).address());
    }

    @Test
    @DisplayName("toUserProfileResponseDto() -> profil bez adresu (null) -> address w DTO null")
    void toUserProfileResponseDto_nullAddress_mapsToNull() {

        UserProfile profile = new UserProfile(
                "Jan", "Kowalski", null,
                new PhoneNumber("+48123456789"),
                LocalDate.of(1990, 1, 15), new ProfilePictureUrl("https://example.com/a.png"));

        assertNull(userMapper.toUserProfileResponseDto(profile).address());
    }

    @Test
    @DisplayName("toAddress() -> AddressDto z samymi nullami / null -> null (kierunek zapisu)")
    void toAddress_emptyOrNullDto_returnsNull() {

        assertNull(userMapper.toAddress(new AddressDto(null, null, null, null)));
        assertNull(userMapper.toAddress(null));
    }

}