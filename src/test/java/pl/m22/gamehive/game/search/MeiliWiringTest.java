package pl.m22.gamehive.game.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.m22.gamehive.game.search.config.MeiliClientConfig;
import pl.m22.gamehive.game.search.service.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "gamehive.search.enabled=true")
class MeiliWiringTest {

    @Autowired GameSearchService gameSearchService;
    @Autowired TaxonomySuggestService taxonomySuggestService;
    @Autowired @Qualifier(MeiliClientConfig.CONTENT_GATEWAY) MeiliIndexGateway contentGateway;
    @Autowired @Qualifier(MeiliClientConfig.TAXONOMY_GATEWAY) MeiliIndexGateway taxonomyGateway;
    @MockitoBean JavaMailSender mailSender;

    @Test
    @DisplayName("z włączoną wyszukiwarką aktywne są implementacje Meili, nie fallbacki")
    void searchEnabled_activatesMeiliImplementations() {
        assertThat(gameSearchService).isInstanceOf(MeiliGameSearchService.class);
        assertThat(taxonomySuggestService).isInstanceOf(MeiliTaxonomySuggestService.class);
    }

    @Test
    @DisplayName("obie bramy istnieją i wskazują RÓŻNE indeksy — dwa indeksy, jeden klient")
    void bothGateways_pointToDistinctIndexes() {
        assertThat(contentGateway.indexUid()).isEqualTo("gamehive_content");
        assertThat(taxonomyGateway.indexUid()).isEqualTo("gamehive_taxonomy");
        assertThat(contentGateway).isNotSameAs(taxonomyGateway);
    }
}
