package pl.m22.gamehive.security;

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
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.user.service.UserService;

import java.util.Objects;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAuthStateCacheTest {

    private static final String CACHE = "userAuthState";
    private static final String JANE = "jane.smith@example.com";

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
        if (!userService.findUserById(2L).isEnabled()) {
            userService.activateUser(2L);
        }
        userService.updateUserRoles(2L, Set.of("ROLE_USER"), new Email("john.doe@example.com")); // przywróć role jane
        flushRedis();
        clearCache();
    }

    @Test
    @DisplayName("Commit 3: dezaktywacja eksmituje wpis usera z cache userAuthState")
    void deactivationEvictsAuthStateCache() throws Exception {
        String janeToken = jwtService.generateToken(JANE, JwtTokenType.ACCESS, Set.of("ROLE_USER"));

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk());
        Assertions.assertNotNull(cache().get(JANE), "wpis powinien trafić do cache po żądaniu");

        mockMvc.perform(patch("/api/v1/admin/users/2/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        Assertions.assertNull(cache().get(JANE), "dezaktywacja powinna natychmiast eksmitować cache");
    }

    @Test
    @DisplayName("Commit 3: zmiana ról eksmituje wpis usera z cache userAuthState")
    void roleUpdateEvictsAuthStateCache() throws Exception {
        String janeToken = jwtService.generateToken(JANE, JwtTokenType.ACCESS, Set.of("ROLE_USER"));

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk());
        Assertions.assertNotNull(cache().get(JANE));

        mockMvc.perform(put("/api/v1/admin/users/2/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_MODERATOR\"]}"))
                .andExpect(status().isOk());

        Assertions.assertNull(cache().get(JANE), "zmiana ról powinna natychmiast eksmitować cache");
    }

    private Cache cache() {
        Cache c = cacheManager.getCache(CACHE);
        Assertions.assertNotNull(c);
        return c;
    }

    private void clearCache() {
        Cache c = cacheManager.getCache(CACHE);
        if (c != null) c.clear();
    }

    private void flushRedis() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }
}
