package pl.m22.gamehive.game.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.game.model.Author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class AuthorRepositoryTest {

    @Autowired
    private AuthorRepository authorRepository;

    @Test
    @DisplayName("findByFirstNameAndLastName -> zwraca zasianego autora")
    void findByPair_ok() {
        assertThat(authorRepository.findByFirstNameAndLastName("Uwe", "Rosenberg")).isPresent();
    }

    @Test
    @DisplayName("existsByFirstNameAndLastName -> true/false")
    void existsByPair() {
        assertThat(authorRepository.existsByFirstNameAndLastName("Uwe", "Rosenberg")).isTrue();
        assertThat(authorRepository.existsByFirstNameAndLastName("Jan", "Kowalski")).isFalse();
    }

    @Test
    @DisplayName("ten sam firstName, inny lastName -> dozwolone (unikat dotyczy pary)")
    void sameFirst_differentLast_allowed() {
        authorRepository.saveAndFlush(Author.of("Uwe", "Nowak"));
        assertThat(authorRepository.existsByFirstNameAndLastName("Uwe", "Nowak")).isTrue();
    }

    @Test
    @DisplayName("duplikat pary firstName+lastName -> naruszenie unikalności")
    void duplicatePair_violatesUnique() {
        assertThatThrownBy(() ->
                authorRepository.saveAndFlush(Author.of("Uwe", "Rosenberg")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
