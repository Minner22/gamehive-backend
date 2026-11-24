package pl.m22.gamehive.auth.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.HashedPassword;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.common.exception.BaseException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.repository.UserRoleRepository;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GH-172: {@link AuthService#loadRefreshCredentials} — polityka "wyłączone konto nie odświeża sesji, a jego ocalałe
 * refresh tokeny są sprzątane best-effort". Test jednostkowy (mocki), end-to-end w {@code RefreshDisabledAccountTest}.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshCredentialsTest {

    private static final Email EMAIL = new Email("jane.smith@example.com");

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserRoleRepository userRoleRepository;
    @Mock UserMapper userMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock JwtService jwtService;
    @InjectMocks AuthServiceImpl authService;

    private final Logger logger = (Logger) LoggerFactory.getLogger(AuthServiceImpl.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("Aktywne konto -> credentials z mappera, bez rewokacji")
    void enabled_returnsCredentials() {
        AppUser user = user(true);
        CredentialsDto credentials = new CredentialsDto(EMAIL.value(), Set.of("ROLE_USER"));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userMapper.toCredentialsDto(user)).thenReturn(credentials);

        assertThat(authService.loadRefreshCredentials(EMAIL)).isSameAs(credentials);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Wyłączone konto -> ACCOUNT_DISABLED i dokładnie jedna rewokacja refreshy tego konta")
    void disabled_revokesAndThrows() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(false)));

        assertErrorCode(() -> authService.loadRefreshCredentials(EMAIL), ErrorCode.ACCOUNT_DISABLED);

        verify(jwtService).revokeUsersTokens(EMAIL.value());
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("Wyłączone konto, Redis niedostępny przy rewokacji -> nadal ACCOUNT_DISABLED + ERROR z zamaskowanym e-mailem")
    void disabled_revokeInfrastructureFailure_stillAccountDisabled() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(false)));
        doThrow(new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot revoke tokens - Redis unavailable"))
                .when(jwtService).revokeUsersTokens(any());

        assertErrorCode(() -> authService.loadRefreshCredentials(EMAIL), ErrorCode.ACCOUNT_DISABLED);

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.ERROR);
            assertThat(e.getFormattedMessage()).contains(EMAIL.obfuscated()).doesNotContain(EMAIL.value());
        });
    }

    @Test
    @DisplayName("Błąd programistyczny przy rewokacji NIE jest połykany (best-effort tylko dla awarii infrastruktury)")
    void disabled_revokeProgrammingError_propagates() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(false)));
        IllegalStateException bug = new IllegalStateException("bug");
        doThrow(bug).when(jwtService).revokeUsersTokens(any());

        assertThatThrownBy(() -> authService.loadRefreshCredentials(EMAIL)).isSameAs(bug);
    }

    @Test
    @DisplayName("Nieznany e-mail -> USER_NOT_FOUND (jak dotychczasowe userService.findUserByEmail)")
    void unknown_userNotFound() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertErrorCode(() -> authService.loadRefreshCredentials(EMAIL), ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(jwtService);
    }

    private static AppUser user(boolean enabled) {
        AppUser user = AppUser.register(new Username("jane_smith"), EMAIL, HashedPassword.fromHash("{noop}secret123"));
        if (enabled) {
            user.activate();
        }
        return user;
    }

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(code);
    }
}
