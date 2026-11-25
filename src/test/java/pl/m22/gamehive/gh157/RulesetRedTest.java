package pl.m22.gamehive.gh157;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// GH-157: celowo czerwony test do weryfikacji rulesetu - PR NIE DO MERGE
class RulesetRedTest {

    @Test
    void deliberatelyRed() {
        assertThat(1).isEqualTo(2);
    }
}
