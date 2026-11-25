package pl.m22.gamehive.common.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingUtilsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "jane.smith@example.com, ja********@example.com",
            "ab@example.com, ab@example.com",
            "abc@example.com, ab*@example.com"
    })
    @DisplayName("Maskuje lokalną część poza dwoma pierwszymi znakami, domena zostaje")
    void obfuscatesLocalPart(String email, String expected) {
        assertThat(LoggingUtils.obfuscateEmail(email)).isEqualTo(expected);
    }

    @Test
    @DisplayName("GH-150: null -> znacznik zamiast NPE (wołane w blokach catch — NPE przykryłby oryginalny błąd)")
    void nullEmail_returnsPlaceholder() {
        assertThat(LoggingUtils.obfuscateEmail(null)).isEqualTo("<empty>");
    }
}
