package pl.m22.gamehive.game.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.game.model.Mechanic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class MechanicRepositoryTest {

    @Autowired
    private MechanicRepository mechanicRepository;

    @Test
    @DisplayName("findByName -> zwraca zasianą mechanikę")
    void findByName_ok() {
        assertThat(mechanicRepository.findByName("Worker Placement")).isPresent();
    }

    @Test
    @DisplayName("existsByName -> true/false")
    void existsByName() {
        assertThat(mechanicRepository.existsByName("Worker Placement")).isTrue();
        assertThat(mechanicRepository.existsByName("Nieznana mechanika")).isFalse();
    }

    @Test
    @DisplayName("duplikat name -> naruszenie unikalności")
    void duplicateName_violatesUnique() {
        assertThatThrownBy(() ->
                mechanicRepository.saveAndFlush(Mechanic.of("Worker Placement")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
