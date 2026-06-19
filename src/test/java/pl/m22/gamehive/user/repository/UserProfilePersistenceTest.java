package pl.m22.gamehive.user.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.user.model.Address;
import pl.m22.gamehive.user.model.UserProfile;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserProfilePersistenceTest {

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("zapis i odczyt UserProfile z wypełnionym Address -> wszystkie 4 kolumny zachowane")
    void persistAndLoad_withFilledAddress() {

        UserProfile profile = new UserProfile(
                "Jan", "Kowalski",
                new Address("ul. Testowa 1", "Warszawa", "00-001", "Polska"),
                null, null, null);

        em.persist(profile);
        em.flush();
        Long id = profile.getId();
        em.clear();

        UserProfile reloaded = em.find(UserProfile.class, id);

        assertThat(reloaded.getAddress()).isNotNull();
        assertThat(reloaded.getAddress().getStreet()).isEqualTo("ul. Testowa 1");
        assertThat(reloaded.getAddress().getCity()).isEqualTo("Warszawa");
        assertThat(reloaded.getAddress().getPostalCode()).isEqualTo("00-001");
        assertThat(reloaded.getAddress().getCountry()).isEqualTo("Polska");
    }

    @Test
    @DisplayName("zapis i odczyt UserProfile z address = null -> brak danych adresowych")
    void persistAndLoad_withNullAddress() {

        UserProfile profile = new UserProfile(
                "Anna", "Nowak", null, null, null, null);

        em.persist(profile);
        em.flush();
        Long id = profile.getId();
        em.clear();

        UserProfile reloaded = em.find(UserProfile.class, id);

        assertThat(reloaded.getFirstName()).isEqualTo("Anna");
        // Hibernate dla pustego embeddable może zwrócić null LUB Address(null,null,null,null)
        assertThat(reloaded.getAddress() == null || reloaded.getAddress().isEmpty()).isTrue();
    }
}
