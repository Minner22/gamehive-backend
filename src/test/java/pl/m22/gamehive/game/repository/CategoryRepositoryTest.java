package pl.m22.gamehive.game.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.game.model.Category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("findByName -> zwraca zasianą kategorię")
    void findByName_ok() {
        assertThat(categoryRepository.findByName("Strategy")).isPresent();
    }

    @Test
    @DisplayName("existsByName -> true/false")
    void existsByName() {
        assertThat(categoryRepository.existsByName("Strategy")).isTrue();
        assertThat(categoryRepository.existsByName("Nieznana")).isFalse();
    }

    @Test
    @DisplayName("duplikat name -> naruszenie unikalności")
    void duplicateName_violatesUnique() {
        assertThatThrownBy(() ->
                categoryRepository.saveAndFlush(Category.of("Strategy")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
