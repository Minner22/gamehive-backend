package pl.m22.gamehive.common.logging;

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
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.model.ContentModerationAction;
import pl.m22.gamehive.game.model.ContentModerationAuditLog;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.repository.CategoryRepository;
import pl.m22.gamehive.game.repository.ContentModerationAuditLogRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.game.repository.PublisherRepository;
import pl.m22.gamehive.support.SeededUsers;
import pl.m22.gamehive.user.model.AuditAction;
import pl.m22.gamehive.user.model.UserAuditLogEntry;
import pl.m22.gamehive.user.repository.UserAuditLogRepository;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pl.m22.gamehive.common.logging.CorrelationIdFilter.CORRELATION_ID_HEADER;
import static pl.m22.gamehive.common.logging.CorrelationIdFilter.MAX_CORRELATION_ID_LENGTH;

/**
 * GH-144, regresja end-to-end: nagłówek {@code X-Correlation-Id} od klienta nie może decydować o tym, czy akcja
 * trafi do audytu. Przed poprawką wartość dłuższa niż 64 znaki dawała 2xx, ale insert do {@code VARCHAR(64)}
 * w {@code REQUIRES_NEW} po commicie padał i wpis po cichu znikał (0 wierszy).
 *
 * <p>Bez {@code @Transactional}: audyt to {@code AFTER_COMMIT}, więc akcja musi się naprawdę zatwierdzić.
 * Stąd własna gra PENDING i sprzątanie w {@code @AfterEach} (wzorzec {@code GameModerationServiceAuditTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrelationIdAuditRegressionTest {

    private static final String TOO_LONG = "x".repeat(500);

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired CacheManager cacheManager;
    @Autowired UserAuditLogRepository userAuditRepository;
    @Autowired ContentModerationAuditLogRepository contentAuditRepository;
    @Autowired GameRepository gameRepository;
    @Autowired PublisherRepository publisherRepository;
    @Autowired CategoryRepository categoryRepository;
    @MockitoBean JavaMailSender mailSender;

    private Long gameId;

    @BeforeEach
    void setUp() {
        resetSecurityState();
        userAuditRepository.deleteAll();
        contentAuditRepository.deleteAll();
    }

    @AfterEach
    void cleanup() {
        if (gameId != null && gameRepository.existsById(gameId)) {
            gameRepository.deleteById(gameId);
        }
        userAuditRepository.deleteAll();
        contentAuditRepository.deleteAll();
        // force-logout ustawia epokę sesji Jane w Redis — nie zostawiamy jej innym klasom testowym
        resetSecurityState();
    }

    @Test
    @DisplayName("force-logout z X-Correlation-Id 500 znaków -> 204 i dokładnie jeden wpis audytu z UUID")
    void forceLogout_withTooLongCorrelationId_stillWritesExactlyOneAuditEntry() throws Exception {
        String adminToken = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS,
                Set.of("ROLE_ADMIN", "ROLE_USER"));

        String responseId = mockMvc.perform(post("/api/v1/admin/users/" + SeededUsers.JANE_ID + "/force-logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header(CORRELATION_ID_HEADER, TOO_LONG))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader(CORRELATION_ID_HEADER);

        List<UserAuditLogEntry> entries = userAuditRepository.findByTargetId(SeededUsers.JANE_ID);
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getAction()).isEqualTo(AuditAction.FORCE_LOGOUT);
        assertReplacedCorrelationId(entries.getFirst().getCorrelationId(), responseId);
    }

    @Test
    @DisplayName("approve gry z X-Correlation-Id 500 znaków -> 200 i dokładnie jeden wpis audytu moderacji z UUID")
    void approve_withTooLongCorrelationId_stillWritesExactlyOneAuditEntry() throws Exception {
        gameId = createPendingGame();
        String moderatorToken = jwtService.generateToken("mark.moderator@example.com", JwtTokenType.ACCESS,
                Set.of("ROLE_MODERATOR"));

        String responseId = mockMvc.perform(post("/api/v1/moderation/games/" + gameId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .header(CORRELATION_ID_HEADER, TOO_LONG))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(CORRELATION_ID_HEADER);

        List<ContentModerationAuditLog> entries =
                contentAuditRepository.findByTargetTypeAndTargetId(ContentModerationTargetType.GAME, gameId);
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getAction()).isEqualTo(ContentModerationAction.APPROVE);
        assertReplacedCorrelationId(entries.getFirst().getCorrelationId(), responseId);
    }

    private static void assertReplacedCorrelationId(String stored, String responseHeader) {
        assertThat(stored).hasSizeLessThanOrEqualTo(MAX_CORRELATION_ID_LENGTH);
        assertThat(UUID.fromString(stored).toString()).isEqualTo(stored);
        // ten sam identyfikator w odpowiedzi i w audycie — klient może z nim przyjść do supportu
        assertThat(responseHeader).isEqualTo(stored);
    }

    private Long createPendingGame() {
        Game game = Game.builder()
                .title("Cel audytu GH-144").description("Gra do decyzji moderacyjnej.")
                .submittedBy(SeededUsers.JANE_ID).moderationStatus(ModerationStatus.PENDING)
                .minPlayers(1).maxPlayers(4).playingTimeMinutes(30)
                .yearPublished(2020).minAge(8).coverImageUrl(null)
                .build();
        game.addPublisher(publisherRepository.findById(1L).orElseThrow());
        game.addCategory(categoryRepository.findById(1L).orElseThrow());
        return gameRepository.saveAndFlush(game).getId();
    }

    private void resetSecurityState() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        Cache cache = cacheManager.getCache("userAuthState");
        if (cache != null) {
            cache.clear();
        }
    }
}
