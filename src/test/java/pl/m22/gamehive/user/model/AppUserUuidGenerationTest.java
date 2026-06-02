package pl.m22.gamehive.user.model;

import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.HashedPassword;
import pl.m22.gamehive.common.domain.Username;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AppUserUuidGenerationTest {

    @Autowired
    private TestEntityManager em;

    private static AppUser newUser(String username, String email) {
        return AppUser.register(
                new Username(username),
                new Email(email),
                HashedPassword.fromHash("{noop}secret123"));
    }

    @Test
    @DisplayName("ID jest null po register() i zostaje przypisany w persist() bez flush/round-tripu do bazy")
    void id_assigned_on_persist_without_flush() {
        AppUser user = newUser("uuiduser", "uuid@example.com");

        assertThat(user.getId()).isNull();          // brak ID po new/register

        em.persist(user);                            // generator Hibernate przypisuje ID w pamięci

        assertThat(user.getId()).isNotNull();        // dostępny natychmiast, bez flush/INSERT
    }

    @Test
    @DisplayName("Wygenerowany ID jest poprawnym UUID v7 (version() == 7)")
    void generated_id_is_version_7() {
        AppUser user = newUser("v7user", "v7@example.com");

        em.persist(user);

        assertThat(user.getId().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("ID są uporządkowane czasowo — rekordy w odstępie >1 ms rosną leksykograficznie")
    void ids_are_time_ordered() throws InterruptedException {
        AppUser first = newUser("first", "first@example.com");
        em.persist(first);

        Thread.sleep(2); // inny znacznik milisekundowy w wysokich bitach v7

        AppUser second = newUser("second", "second@example.com");
        em.persist(second);

        assertThat(first.getId().toString())
                .isLessThan(second.getId().toString());
    }

    @Test
    @DisplayName("equals/hashCode działają z proxy Hibernate (porównanie po klasie docelowej i ID)")
    void equals_and_hashCode_handle_hibernate_proxy() {
        AppUser user = newUser("proxyuser", "proxy@example.com");
        em.persist(user);
        em.flush();
        UUID id = user.getId();
        em.clear(); // wyczyść kontekst, by getReference zwrócił nieinicjalizowane proxy

        AppUser proxy = em.getEntityManager().getReference(AppUser.class, id);
        assertThat(proxy).isInstanceOf(HibernateProxy.class);

        // 'user' jest teraz odłączoną, zwykłą instancją o tym samym ID
        assertThat(proxy).isEqualTo(user);                       // proxy.equals(real): gałąź proxy po stronie this
        assertThat(user).isEqualTo(proxy);                       // real.equals(proxy): gałąź proxy po stronie o
        assertThat(proxy.hashCode()).isEqualTo(user.hashCode()); // gałąź proxy w hashCode
    }
}
