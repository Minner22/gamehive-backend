package pl.m22.gamehive.user.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.support.SeededUsers;
import pl.m22.gamehive.user.dto.AuditLogFilter;
import pl.m22.gamehive.user.model.AuditAction;
import pl.m22.gamehive.user.model.UserAuditLogEntry;
import pl.m22.gamehive.user.repository.UserAuditLogRepository;

import java.time.Instant;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AuditLogQueryServiceTest {

    @Autowired AuditLogQueryService auditLogQueryService;
    @Autowired UserAuditLogRepository auditLogRepository;

    private static final PageRequest PAGE =
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

    @BeforeEach
    void seed() {
        auditLogRepository.deleteAll();
        // 2x target JANE (actor john): ROLE_CHANGE, DEACTIVATE; 1x target JOHN (actor jane): FORCE_LOGOUT
        auditLogRepository.save(UserAuditLogEntry.of(AuditAction.ROLE_CHANGE, SeededUsers.JANE_ID,
                "jane.smith@example.com", "john.doe@example.com",
                "{\"before\":[\"ROLE_USER\"],\"after\":[\"ROLE_MODERATOR\"]}", "c1"));
        auditLogRepository.save(UserAuditLogEntry.of(AuditAction.DEACTIVATE, SeededUsers.JANE_ID,
                "jane.smith@example.com", "john.doe@example.com", null, "c2"));
        auditLogRepository.save(UserAuditLogEntry.of(AuditAction.FORCE_LOGOUT, SeededUsers.JOHN_ID,
                "john.doe@example.com", "jane.smith@example.com", null, "c3"));
    }

    @AfterEach
    void cleanup() {
        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("search() bez filtrów -> cała historia")
    void search_noFilter_returnsAll() {

        Page<UserAuditLogEntry> page = auditLogQueryService.search(
                new AuditLogFilter(null, null, null, null, null), PAGE);

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("search() filtr targetId -> tylko zdarzenia danego usera")
    void search_byTargetId() {

        Page<UserAuditLogEntry> page = auditLogQueryService.search(
                new AuditLogFilter(SeededUsers.JANE_ID, null, null, null, null), PAGE);

        assertThat(page.getContent())
                .hasSize(2)
                .allMatch(e -> e.getTargetId().equals(SeededUsers.JANE_ID));
    }

    @Test
    @DisplayName("search() filtr actor -> tylko zdarzenia danego wykonawcy")
    void search_byActor() {

        Page<UserAuditLogEntry> page = auditLogQueryService.search(
                new AuditLogFilter(null, "jane.smith@example.com", null, null, null), PAGE);

        assertThat(page.getContent())
                .hasSize(1)
                .allMatch(e -> e.getActor().equals("jane.smith@example.com"));
    }

    @Test
    @DisplayName("search() filtr action -> tylko dany typ akcji")
    void search_byAction() {

        Page<UserAuditLogEntry> page = auditLogQueryService.search(
                new AuditLogFilter(null, null, AuditAction.DEACTIVATE, null, null), PAGE);

        assertThat(page.getContent())
                .hasSize(1)
                .allMatch(e -> e.getAction() == AuditAction.DEACTIVATE);
    }

    @Test
    @DisplayName("search() filtry łączą się AND (targetId + action)")
    void search_combinedAnd() {

        Page<UserAuditLogEntry> page = auditLogQueryService.search(
                new AuditLogFilter(SeededUsers.JANE_ID, null, AuditAction.ROLE_CHANGE, null, null), PAGE);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getAction()).isEqualTo(AuditAction.ROLE_CHANGE);
    }

    @Test
    @DisplayName("search() AND nie daje przecięcia -> pusto (targetId JOHN + action DEACTIVATE)")
    void search_combinedAnd_noMatch() {

        Page<UserAuditLogEntry> page = auditLogQueryService.search(
                new AuditLogFilter(SeededUsers.JOHN_ID, null, AuditAction.DEACTIVATE, null, null), PAGE);

        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("search() zakres czasu: from w przeszłości -> wszystko; to w przeszłości -> nic")
    void search_timeRange() {

        Instant pastBoundary = Instant.parse("2000-01-01T00:00:00Z");

        Page<UserAuditLogEntry> fromPast = auditLogQueryService.search(
                new AuditLogFilter(null, null, null, pastBoundary, null), PAGE);
        assertThat(fromPast.getTotalElements()).isEqualTo(3);

        Page<UserAuditLogEntry> toPast = auditLogQueryService.search(
                new AuditLogFilter(null, null, null, null, pastBoundary), PAGE);
        assertThat(toPast.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("search() sortuje malejąco po createdAt")
    void search_sortedByCreatedAtDesc() {

        Page<UserAuditLogEntry> page = auditLogQueryService.search(
                new AuditLogFilter(null, null, null, null, null), PAGE);

        // non-strict: @PrePersist nie gwarantuje rozłącznych znaczników -> dopuszczamy remisy
        assertThat(page.getContent())
                .isSortedAccordingTo(Comparator.comparing(UserAuditLogEntry::getCreatedAt).reversed());
    }
}