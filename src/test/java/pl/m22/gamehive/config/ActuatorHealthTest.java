package pl.m22.gamehive.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GH-159: actuator wystawia wyłącznie {@code health} (z sondami liveness/readiness) i tylko ten endpoint jest
 * publiczny — healthcheck kontenera nie ma tokena. Odpowiedź niesie sam status: bez {@code components}/{@code details},
 * czyli bez nazw usług i wersji dla anonimowego wołającego. Pozostałe endpointy actuatora nie istnieją nawet dla admina.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorHealthTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @MockitoBean JavaMailSender mailSender;

    @Test
    @DisplayName("anonim GET /actuator/health -> 200 UP, bez components i details")
    void health_publicStatusOnly() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness"})
    @DisplayName("anonim -> sondy liveness/readiness 200 UP")
    void probes_public(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/env", "/actuator/beans", "/actuator/heapdump"})
    @DisplayName("anonim -> pozostałe ścieżki actuatora 401 (nie są permitAll)")
    void otherActuatorPaths_requireAuthentication(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/info"})
    @DisplayName("admin -> niewystawione endpointy 404 (exposure = tylko health)")
    void notExposedEndpoints_notFoundEvenForAdmin(String path) throws Exception {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN"));

        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
