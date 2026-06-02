package pl.m22.gamehive.common.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.m22.gamehive.user.model.UserProfile;
import pl.m22.gamehive.user.model.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

class EntityEqualsHashCodeTest {

    @Test
    @DisplayName("Encje transient (id == null) są równe tylko jako ta sama instancja")
    void transient_entities_not_equal_unless_same_instance() {
        UserRole a = new UserRole("ROLE_USER", null);
        UserRole b = new UserRole("ROLE_USER", null);

        assertThat(a).isEqualTo(a);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Ta sama klasa + to samo ID -> równe + równy hashCode (pozostałe pola bez znaczenia)")
    void same_id_means_equal() {
        UserRole a = new UserRole("ROLE_USER", null);
        a.setId(5L);
        UserRole b = new UserRole("ROLE_ADMIN", "inny opis");
        b.setId(5L);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("Różne ID -> encje różne")
    void different_id_not_equal() {
        UserRole a = new UserRole("ROLE_USER", null);
        a.setId(1L);
        UserRole b = new UserRole("ROLE_USER", null);
        b.setId(2L);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("equals(null) -> false")
    void not_equal_to_null() {
        UserRole role = new UserRole("ROLE_USER", null);
        role.setId(1L);

        assertThat(role.equals(null)).isFalse();
    }

    @Test
    @DisplayName("Encje różnych klas z tym samym ID nie są równe")
    void different_class_same_id_not_equal() {
        UserRole role = new UserRole("ROLE_USER", null);
        role.setId(5L);
        UserProfile profile = new UserProfile();
        profile.setId(5L);

        assertThat(role).isNotEqualTo(profile);
    }

    @Test
    @DisplayName("hashCode stabilny przed i po przypisaniu ID (kontrakt dla HashSet)")
    void hashCode_stable_across_id_assignment() {
        UserRole r = new UserRole("ROLE_USER", null);
        int before = r.hashCode();

        r.setId(9L);

        assertThat(r.hashCode()).isEqualTo(before);
    }
}

