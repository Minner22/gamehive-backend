package pl.m22.gamehive.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.LoginDto;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.auth.event.PasswordResetRequestedEvent;
import pl.m22.gamehive.auth.event.UserRegisteredEvent;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.user.event.UserCredentialsChangedEvent;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserRole;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.repository.UserRoleRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{

    private static final String USER_ROLE = "ROLE_USER";
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public void register(RegistrationDto registrationDto) {

        AppUser appUser = registerUser(registrationDto);

        eventPublisher.publishEvent(new UserRegisteredEvent(appUser.getEmail()));
    }

    @Override
    public CredentialsDto login(LoginDto loginDto) {

        String identifier = loginDto.usernameOrEmail();
        AppUser appUser = userRepository.findByEmailOrUsername(identifier, identifier)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USERNAME_OR_EMAIL_NOT_FOUND, "Username or email not found: " + identifier));

        if (!appUser.isEnabled()) {
            throw new ApplicationException(ErrorCode.USER_NOT_ACTIVATED, "User not activated: " + appUser.getEmail());
        }

        if (!passwordEncoder.matches(loginDto.password(), appUser.getPassword())) {
            throw new DomainException(ErrorCode.INVALID_PASSWORD);
        }

        return userMapper.toCredentialsDto(appUser);
    }

    @Transactional
    @Override
    public void activateUser(String email) {

        AppUser appUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.EMAIL_NOT_FOUND, "Email not found: " + email));

        appUser.activate();

        userRepository.save(appUser);
    }

    @Transactional(readOnly = true)
    @Override
    public void requestPasswordReset(String email) {

        Optional<AppUser> appUser = userRepository.findByEmail(email);

        if (appUser.isPresent()) {
            eventPublisher.publishEvent(new PasswordResetRequestedEvent(email));
        }
    }

    @Transactional
    @Override
    public void confirmPasswordReset(String email, String newPassword) {

        AppUser appUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.EMAIL_NOT_FOUND, "Email not found: " + email));

        appUser.changePassword(passwordEncoder.encode(newPassword));

        userRepository.save(appUser);

        // @Transactional jest tu wymagane: listener UserCredentialsChangedEvent jest AFTER_COMMIT,
        // bez aktywnej transakcji zdarzenie nie zostałoby dostarczone (sesje nie zostałyby unieważnione).
        eventPublisher.publishEvent(new UserCredentialsChangedEvent(email));
    }

    private AppUser registerUser(RegistrationDto registrationDto) {

        if (userRepository.existsByEmail(registrationDto.email())) {
            throw new DomainException(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "Email already exists: " + registrationDto.email());
        }

        if (userRepository.existsByUsername(registrationDto.username())) {
            throw new DomainException(ErrorCode.USERNAME_ALREADY_EXISTS,
                    "Username already exists: " + registrationDto.username());
        }

        AppUser appUser = AppUser.register(
                registrationDto.username(),
                registrationDto.email(),
                passwordEncoder.encode(registrationDto.password())
        );

        UserRole role = userRoleRepository.findByName(USER_ROLE)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ROLE_NOT_FOUND,
                        "Role not found: " + USER_ROLE));

        appUser.assignRole(role);

        return userRepository.save(appUser);
    }
}
