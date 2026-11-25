package pl.m22.gamehive.common.logging;

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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GH-155: {@link LoggingAspect} faktycznie owija kontrolery. Aspekt działa tylko wtedy, gdy na classpath jest
 * starter AOP z autokonfiguracją ({@code spring-boot-starter-aspectj} w Boot 4 — dawny {@code starter-aop}); bez
 * niej bean {@code @Aspect} powstaje, ale nic go nie stosuje, a reszta suity zostaje zielona.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoggingAspectTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @MockitoBean JavaMailSender mailSender;

    private final Logger aspectLogger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        aspectLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        aspectLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("Żądanie do @RestController -> INFO 'Handled request:' z nazwą kontrolera, metody i czasem")
    void restControllerCall_isLoggedByAspect() throws Exception {
        String token = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));

        mockMvc.perform(get("/api/v1/taxonomy/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(appender.list)
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getLevel()).isEqualTo(Level.INFO);
                    assertThat(e.getFormattedMessage())
                            .matches("Handled request: controller=TaxonomyController method=listCategories duration=\\d+ms");
                });
    }
}
