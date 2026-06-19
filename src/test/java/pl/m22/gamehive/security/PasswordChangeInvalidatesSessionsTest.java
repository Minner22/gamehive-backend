package pl.m22.gamehive.security;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.user.repository.UserRepository;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordChangeInvalidatesSessionsTest {

    private static final String EMAIL = "ctrl_pwchange@test.com";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired CacheManager cacheManager;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        flushRedis();
        clearCache();
    }

    @AfterEach
    void tearDown() {
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
        flushRedis();
        clearCache();
    }

    @Test
    @DisplayName("Commit 4: zmiana hasła unieważnia stare access i refresh tokeny")
    void passwordChangeInvalidatesSessions() throws Exception {
        // register (enabled=false) + activate
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"username":"ctrl_pwchange","email":"%s","password":"oldPassword1"}
                                  """.formatted(EMAIL)))
                .andExpect(status().isOk());

        String activation = jwtService.generateToken(EMAIL, JwtTokenType.ACTIVATION, null);
        mockMvc.perform(get("/api/v1/auth/activate").param("token", activation))
                .andExpect(status().isOk());

        // login -> access (body) + refresh (cookie zapisany w Redis)
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"email":"%s","password":"oldPassword1"}
                                  """.formatted(EMAIL)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        Assertions.assertNotNull(setCookie);
        String refreshToken = setCookie.split("refreshToken=")[1].split(";")[0];

        // zmiana hasła przez reset (commit -> AFTER_COMMIT -> revoke + epoch + evict)
        String reset = jwtService.generateToken(EMAIL, JwtTokenType.PASSWORD_RESET, null);
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"token":"%s","newPassword":"newPassword1"}
                                  """.formatted(reset)))
                .andExpect(status().isOk());

        // stary access -> 401 TOKEN_REVOKED (iat < epoch)
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REVOKED"));

        // stary refresh -> 401 (JTI zrewokowane)
        mockMvc.perform(get("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    private void clearCache() {
        Cache c = cacheManager.getCache("userAuthState");
        if (c != null) c.clear();
    }

    private void flushRedis() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }
}
