package pl.m22.gamehive.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.support.SeededUsers;
import pl.m22.gamehive.user.service.UserService;

import java.util.Objects;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessTokenInvalidationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserService userService;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired CacheManager cacheManager;
    @MockitoBean JavaMailSender mailSender;

    private String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        flushRedis();
        clearCache();
    }

    @AfterEach
    void tearDown() {
        if (!userService.findUserById(SeededUsers.JANE_ID).isEnabled()) {
            userService.activateUser(SeededUsers.JANE_ID);
        }
        flushRedis();
    }

    @Test
    @DisplayName("Commit 2: po dezaktywacji access token -> 401 ACCOUNT_DISABLED")
    void accessTokenRejectedWithAccountDisabled() throws Exception {
        String janeToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));

        mockMvc.perform(patch("/api/v1/admin/users/" + SeededUsers.JANE_ID + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("Commit 2: reaktywacja nie przywraca starego access tokenu -> 401 TOKEN_REVOKED")
    void oldAccessTokenStaysRevokedAfterReactivation() throws Exception {
        String janeToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));

        mockMvc.perform(patch("/api/v1/admin/users/" + SeededUsers.JANE_ID + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/users/" + SeededUsers.JANE_ID + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REVOKED"));
    }

    @Test
    @DisplayName("Commit 2: dezaktywacja jednego usera nie rusza tokenów innych (izolacja)")
    void deactivationDoesNotAffectOtherUsers() throws Exception {
        String johnToken = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));

        mockMvc.perform(patch("/api/v1/admin/users/" + SeededUsers.JANE_ID + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + johnToken))
                .andExpect(status().isOk());
    }

    private void flushRedis() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    private void clearCache() {
        Cache c = cacheManager.getCache("userAuthState");

        if (c != null) {
            c.clear();
        }
    }
}