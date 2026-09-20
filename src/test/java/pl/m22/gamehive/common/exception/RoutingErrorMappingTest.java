package pl.m22.gamehive.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoutingErrorMappingTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

    private String adminToken;
    private Logger handlerLogger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        adminToken = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));

        // kryterium akceptacji #140: te przypadki nie mogą trafiać do logu jako ERROR ze stack trace'em
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logs = new ListAppender<>();
        logs.start();
        handlerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(logs);
        logs.stop();
    }

    @Test
    @DisplayName("PUT na ścieżce, która ma tylko DELETE -> 405 + Allow: DELETE")
    void unsupportedMethod_returns405WithAllow() throws Exception {
        mockMvc.perform(put("/api/v1/admin/taxonomy/publishers/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("DELETE")))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("PATCH na ścieżce z PUT i DELETE -> 405, Allow wymienia obie metody")
    void unsupportedMethod_allowListsEveryMappedMethod() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/taxonomy/categories/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("PUT")))
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("DELETE")));
    }

    @Test
    @DisplayName("Nieistniejąca ścieżka pod /api/v1/** -> 404 RESOURCE_NOT_FOUND w formacie ApiError")
    void unknownPath_returns404ApiError() throws Exception {
        mockMvc.perform(get("/api/v1/admin/taxonomy/nope")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Nieobsługiwany Content-Type -> 415 UNSUPPORTED_MEDIA_TYPE")
    void unsupportedContentType_returns415() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"name\":\"Nowa\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("Accept: application/xml -> 406 z ciałem ApiError (nie 401 z forwardu na /error)")
    void unacceptableAcceptHeader_returns406WithApiError() throws Exception {
        mockMvc.perform(get("/api/v1/admin/taxonomy/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.errorCode").value("NOT_ACCEPTABLE"));
    }

    @Test
    @DisplayName("GET /auth/activate bez ?token= -> 400 VALIDATION_ERROR (było 500); ścieżka publiczna, więc bez tokena")
    void missingRequiredRequestParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/auth/activate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("405 przy Accept: application/xml nadal niesie ciało ApiError, nie samo status + Allow")
    void unsupportedMethodWithXmlAccept_keepsApiErrorBody() throws Exception {
        // bez wymuszonego Content-Type zapis ciała przegrywał negocjację treści, Spring wołał
        // response.sendError(...), a kontener zamieniał to na dispatch ERROR na /error -> 401
        mockMvc.perform(put("/api/v1/admin/taxonomy/publishers/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("DELETE")))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Błąd domenowy przy Accept: application/xml też niesie ApiError (dotyczy buildResponse, nie tylko routingu)")
    void domainErrorWithXmlAccept_keepsApiErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/games/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @Test
    @DisplayName("Bez tokena nadal 401 — security biegnie przed dispatcherem (kontrakt bez zmian)")
    void unsupportedMethodWithoutToken_stillReturns401() throws Exception {
        mockMvc.perform(put("/api/v1/admin/taxonomy/publishers/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Żaden błąd routingu nie loguje się jako ERROR")
    void routingErrors_areNeverLoggedAsError() throws Exception {
        mockMvc.perform(put("/api/v1/admin/taxonomy/publishers/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));
        mockMvc.perform(get("/api/v1/admin/taxonomy/nope")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));
        mockMvc.perform(post("/api/v1/admin/taxonomy/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.TEXT_PLAIN)
                .content("x"));

        // bez tej asercji test przechodziłby także wtedy, gdyby appender nie łapał niczego
        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).extracting(ILoggingEvent::getLevel).doesNotContain(Level.ERROR);
    }
}
