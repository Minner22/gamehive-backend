package pl.m22.gamehive.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("GET /v3/api-docs -> 200 i poprawny dokument OpenAPI (publiczny dostęp)")
    void apiDocs_isGeneratedWithoutErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("GameHive API"));
    }

    @Test
    @DisplayName("Kolejki moderacji dokumentują parametr status ograniczony do PENDING/REJECTED")
    void moderationQueues_documentStatusParameter() throws Exception {
        // springdoc wkleja enum inline w schemat parametru — komponentu ModerationQueueStatus nie ma
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/moderation/games'].get.parameters[?(@.name == 'status')].schema.enum[*]",
                        containsInAnyOrder("PENDING", "REJECTED")))
                .andExpect(jsonPath("$.paths['/api/v1/moderation/games'].get.parameters[?(@.name == 'status')].schema.default",
                        hasItem("PENDING")))
                .andExpect(jsonPath("$.paths['/api/v1/moderation/expansions'].get.parameters[?(@.name == 'status')].schema.enum[*]",
                        containsInAnyOrder("PENDING", "REJECTED")))
                .andExpect(jsonPath("$.paths['/api/v1/moderation/expansions'].get.parameters[?(@.name == 'status')].schema.default",
                        hasItem("PENDING")));
    }

    @Test
    @DisplayName("Dokument zawiera schemat bezpieczeństwa bearerAuth")
    void apiDocs_containsBearerAuthSecurityScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    @DisplayName("GET /swagger-ui.html jest publiczny (przekierowanie, nie 401)")
    void swaggerUi_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Dokument zawiera kluczowe ścieżki API")
    void apiDocs_containsKeyPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/games']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/games/search']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/moderation/games/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/expansions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/moderation/expansions/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collection/games']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/collection/expansions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/users/']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/search/reindex']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/taxonomy/publishers/suggest']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/taxonomy/authors/suggest']").exists());
    }

    @Test
    @DisplayName("Rosnące listy taksonomii są deprecated na rzecz /suggest, kuratorowane zostają bez zmian")
    void apiDocs_marksGrowingTaxonomyListsDeprecated() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/taxonomy/publishers'].get.deprecated").value(true))
                .andExpect(jsonPath("$.paths['/api/v1/taxonomy/authors'].get.deprecated").value(true))
                // kategorie i mechaniki są kuratorowane i bounded — świadomie NIE są oznaczone
                .andExpect(jsonPath("$.paths['/api/v1/taxonomy/categories'].get.deprecated").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/taxonomy/mechanics'].get.deprecated").doesNotExist());
    }

    @Test
    @DisplayName("Podpowiedzi dokumentują 400 — limit jest prymitywem, więc ?limit=abc realnie daje VALIDATION_ERROR")
    void apiDocs_documentsBadRequestOnSuggest() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/taxonomy/publishers/suggest'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/taxonomy/authors/suggest'].get.responses['400']").exists());
    }

    @Test
    @DisplayName("Wspólne odpowiedzi deklarowane raz na poziomie klasy trafiają do każdej operacji")
    void apiDocs_mergesClassLevelResponsesIntoOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // SearchAdminController: metodowo zadeklarowane są tylko 200 i 409...
                .andExpect(jsonPath("$.paths['/api/v1/admin/search/reindex'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/search/reindex'].post.responses['409']").exists())
                // ...a 401/403/500/503 pochodzą wyłącznie z adnotacji na klasie. 503 jest tu sondą
                // rozstrzygającą: springdoc nie tworzy go z żadnego domyślnego mechanizmu, więc jego
                // obecność dowodzi, że scalanie klasa -> metoda faktycznie działa.
                .andExpect(jsonPath("$.paths['/api/v1/admin/search/reindex'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/search/reindex'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/search/reindex'].post.responses['500']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/search/reindex'].post.responses['503']").exists())
                // AdminUserController — wzorzec opisany w CLAUDE.md, dotąd niepilnowany żadną asercją
                .andExpect(jsonPath("$.paths['/api/v1/admin/users/'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/users/'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/users/'].get.responses['500']").exists());
    }
}
