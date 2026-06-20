package pl.m22.gamehive.auth.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.LoginDto;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.auth.event.ActivationEmailResendRequestedEvent;
import pl.m22.gamehive.auth.event.PasswordResetRequestedEvent;
import pl.m22.gamehive.auth.event.UserRegisteredEvent;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.HashedPassword;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.logging.CorrelationIdFilter;
import pl.m22.gamehive.user.event.UserAuditEvent;
import pl.m22.gamehive.user.event.UserCredentialsChangedEvent;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.AuditAction;
import pl.m22.gamehive.user.model.UserRole;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.repository.UserRoleRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{

    private static final String USER_ROLE = "ROLE_USER";
    private static final String EMAIL_NOT_FOUND_MSG = "Email not found: ";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;

    // Atrapa hasha do wyrównania czasu odpowiedzi dla nieistniejących kont (anty-timing).
    // Generowana tym samym encoderem => ten sam algorytm i koszt (bcrypt/10) co realne hasła.
    private String dummyHash;

    @PostConstruct
    void initDummyHash() {
        this.dummyHash = passwordEncoder.encode("invalid-credentials-timing-placeholder");
    }


    @Transactional
    @Override
    public void register(RegistrationDto registrationDto) {

        AppUser appUser = registerUser(registrationDto);

        eventPublisher.publishEvent(new UserRegisteredEvent(appUser.getEmail().value()));
    }

    @Override
    public CredentialsDto login(LoginDto loginDto) {

        Email email = new Email(loginDto.email());
        Optional<AppUser> appUserOpt = userRepository.findByEmail(email);

        // Hasło sprawdzane ZAWSZE i jako pierwsze — także dla nieistniejącego konta (stały czas).
        boolean passwordMatches = appUserOpt
                .map(user -> user.getPassword().matches(loginDto.password(), passwordEncoder))
                .orElseGet(() -> {
                    passwordEncoder.matches(loginDto.password(), dummyHash); // atrapa, wynik ignorowany
                    return false;
                });

        if (!passwordMatches) {
            // Jednolicie: "brak konta" == "złe hasło" — brak enumeracji.
            throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS,
                    "Invalid credentials for: " + email.obfuscated());
        }

        AppUser appUser = appUserOpt.orElseThrow(); // gwarantowanie obecny, skoro hasło pasuje

        // Stan aktywacji ujawniany dopiero PO poprawnym haśle (tylko właściciel konta).
        if (!appUser.isEnabled()) {
            throw new ApplicationException(ErrorCode.USER_NOT_ACTIVATED,
                    "User not activated: " + appUser.getEmail().obfuscated());
        }

        return userMapper.toCredentialsDto(appUser);
    }

    @Transactional
    @Override
    public void activateUser(Email email) {

        AppUser appUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.EMAIL_NOT_FOUND, EMAIL_NOT_FOUND_MSG + email.obfuscated()));

        appUser.activate();

        userRepository.save(appUser);
    }

    @Transactional(readOnly = true)
    @Override
    public void requestPasswordReset(Email email) {

        Optional<AppUser> appUser = userRepository.findByEmail(email);

        if (appUser.isPresent()) {
            eventPublisher.publishEvent(new PasswordResetRequestedEvent(email.value()));
        }
    }

    @Transactional
    @Override
    public void confirmPasswordReset(Email email, String newPassword) {

        AppUser appUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.EMAIL_NOT_FOUND, EMAIL_NOT_FOUND_MSG + email.obfuscated()));

        appUser.changePassword(HashedPassword.fromRaw(newPassword, passwordEncoder));

        userRepository.save(appUser);

        // @Transactional jest tu wymagane: listener UserCredentialsChangedEvent jest AFTER_COMMIT,
        // bez aktywnej transakcji zdarzenie nie zostałoby dostarczone (sesje nie zostałyby unieważnione).
        eventPublisher.publishEvent(new UserCredentialsChangedEvent(email.value()));
        eventPublisher.publishEvent(new UserAuditEvent(
                AuditAction.PASSWORD_CHANGE,
                appUser.getId(),
                email.value(),
                email.value(),
                null,
                MDC.get(CorrelationIdFilter.CORRELATION_ID))
        );
    }

    @Transactional(readOnly = true)
    @Override
    public void resendActivationEmail(Email email) {

        userRepository.findByEmail(email)
                .filter(user -> !user.isEnabled())
                .ifPresent(_ -> eventPublisher.publishEvent(new ActivationEmailResendRequestedEvent(email.value())));
    }

    private AppUser registerUser(RegistrationDto registrationDto) {

        Email email = new Email(registrationDto.email());
        Username username = new Username(registrationDto.username());

        if (userRepository.existsByEmail(email)) {
            throw new DomainException(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "Email already exists: " + email.obfuscated());
        }

        if (userRepository.existsByUsername(username)) {
            throw new DomainException(ErrorCode.USERNAME_ALREADY_EXISTS,
                    "Username already exists: " + username);
        }

        AppUser appUser = AppUser.register(
                username,
                email,
                HashedPassword.fromRaw(registrationDto.password(), passwordEncoder)
        );

        UserRole role = userRoleRepository.findByName(USER_ROLE)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ROLE_NOT_FOUND,
                        "Role not found: " + USER_ROLE));

        appUser.assignRole(role);

        return userRepository.save(appUser);
    }
}
