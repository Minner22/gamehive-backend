package pl.m22.gamehive.civerify;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Jednorazowy test do weryfikacji CI (GH-153) — tylko na gałęzi GH-153-weryfikacja-ci, nigdy nie mergowany.
class DeliberatelyFailingTest {

    @Test
    void failsOnPurposeToProduceSurefireArtifact() {
        assertThat(1).as("celowy błąd: sprawdzamy artefakt surefire-reports po nieudanym runie").isEqualTo(2);
    }
}
