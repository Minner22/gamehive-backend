package pl.m22.gamehive.game.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.game.model.Publisher;
import pl.m22.gamehive.game.model.PublisherStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class PublisherRepositoryTest {

    @Autowired
    private PublisherRepository publisherRepository;

    @Test
    @DisplayName("findByName -> zwraca zasianego wydawcę ze statusem APPROVED")
    void findByName_ok() {
        Optional<Publisher> p = publisherRepository.findByName("Rio Grande Games");
        assertThat(p).isPresent();
        assertThat(p.get().getStatus()).isEqualTo(PublisherStatus.APPROVED);
    }

    @Test
    @DisplayName("existsByName -> true dla istniejącego, false dla nieznanego")
    void existsByName() {
        assertThat(publisherRepository.existsByName("Rio Grande Games")).isTrue();
        assertThat(publisherRepository.existsByName("Nieznany")).isFalse();
    }

    @Test
    @DisplayName("zapis wydawcy PENDING -> odczyt zachowuje status i ustawia createdAt")
    void saveAndRead_pending() {
        Publisher saved = publisherRepository.save(Publisher.of("Nowy Wydawca", PublisherStatus.PENDING));

        Publisher reloaded = publisherRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Nowy Wydawca");
        assertThat(reloaded.getStatus()).isEqualTo(PublisherStatus.PENDING);
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("duplikat name -> naruszenie unikalności")
    void duplicateName_violatesUnique() {
        assertThatThrownBy(() ->
                publisherRepository.saveAndFlush(Publisher.of("Rio Grande Games", PublisherStatus.APPROVED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
