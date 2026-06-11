package pl.m22.gamehive.user.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import pl.m22.gamehive.support.SeededUsers;
import pl.m22.gamehive.user.model.AuditAction;
import pl.m22.gamehive.user.model.UserAuditLogEntry;
import pl.m22.gamehive.user.repository.UserAuditLogRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuditControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserAuditLogRepository auditLogRepository;
    @MockitoBean JavaMailSender mailSender;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        userToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));

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

    // --- autoryzacja ---

    @Test
    @DisplayName("GET /api/v1/admin/audit jako ADMIN -> 200 + pełna historia")
    void getAudit_asAdmin_200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].action").exists())
                .andExpect(jsonPath("$.content[0].correlationId").exists());
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit jako USER -> 403")
    void getAudit_asUser_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit bez tokena -> 401")
    void getAudit_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isUnauthorized());
    }

    // --- filtry ---

    @Test
    @DisplayName("filtr targetId -> tylko zdarzenia danego usera")
    void getAudit_filterByTargetId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("targetId", SeededUsers.JANE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].targetId", everyItem(is(SeededUsers.JANE_ID.toString()))));
    }

    @Test
    @DisplayName("filtr actor -> tylko zdarzenia danego wykonawcy")
    void getAudit_filterByActor() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("actor", "jane.smith@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("FORCE_LOGOUT"));
    }

    @Test
    @DisplayName("filtr action -> tylko dany typ akcji")
    void getAudit_filterByAction() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("action", "DEACTIVATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("DEACTIVATE"));
    }

    @Test
    @DisplayName("filtry AND (targetId + action) -> przecięcie")
    void getAudit_filterCombinedAnd() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("targetId", SeededUsers.JANE_ID.toString())
                        .param("action", "ROLE_CHANGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].details", containsString("ROLE_MODERATOR")));
    }

    // --- zakres czasu (binding Instant + kierunek) ---

    @Test
    @DisplayName("filtr from w przeszłości -> cała historia")
    void getAudit_filterFromPast() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("from", "2000-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("filtr to w przeszłości -> pusto")
    void getAudit_filterToPast() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("to", "2000-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("zakres from..to obejmujący teraz -> cała historia")
    void getAudit_filterRangeBracketingNow() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("from", "2000-01-01T00:00:00Z")
                        .param("to", "2999-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("from + action (AND) -> przecięcie zawężone czasowo")
    void getAudit_filterFromAndAction() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("from", "2000-01-01T00:00:00Z")
                        .param("action", "ROLE_CHANGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("ROLE_CHANGE"));
    }

    // --- stronicowanie i sort ---

    @Test
    @DisplayName("stronicowanie: size=1 -> 1 element na stronie, totalElements=3")
    void getAudit_pagination() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("sort domyślny: createdAt malejąco (najnowsze pierwsze)")
    void getAudit_defaultSortDesc() throws Exception {
        String json = mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // createdAt to ISO-8601 UTC -> porządek leksykalny == chronologiczny; remisy dozwolone (non-strict)
        List<String> createdAt = JsonPath.read(json, "$.content[*].createdAt");
        assertThat(createdAt).isSortedAccordingTo(Comparator.<String>naturalOrder().reversed());
    }

    // --- walidacja (400) ---

    @Test
    @DisplayName("zły format action -> 400 VALIDATION_ERROR")
    void getAudit_invalidAction_400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("action", "NOT_AN_ACTION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("zły format targetId -> 400 VALIDATION_ERROR")
    void getAudit_invalidTargetId_400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("targetId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("zły format from -> 400 VALIDATION_ERROR")
    void getAudit_invalidFrom_400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("from", "wczoraj"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}