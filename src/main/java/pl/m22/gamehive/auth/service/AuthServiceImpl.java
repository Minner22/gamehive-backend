package pl.m22.gamehive.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.LoginDto;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.common.exception.*;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserProfile;
import pl.m22.gamehive.user.model.UserRole;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.repository.UserRoleRepository;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{

    private static final String USER_ROLE = "USER";
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;

    @Transactional
    @Override
    public void register(RegistrationDto registrationDto) {
        if (userRepository.existsByEmail(registrationDto.email())) {
            throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already exists: " + registrationDto.email());
        }

        if (userRepository.existsByUsername(registrationDto.username())) {
            throw new ApplicationException(ErrorCode.USERNAME_ALREADY_EXISTS, "Username already exists: " + registrationDto.username());
        }

        AppUser appUser = userMapper.toUser(registrationDto);
        appUser.setEnabled(false);
        appUser.setPassword(passwordEncoder.encode(registrationDto.password()));
        appUser.setUserProfile(new UserProfile());

        UserRole role = userRoleRepository.findByName(USER_ROLE)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ROLE_NOT_FOUND, "Role not found: " + USER_ROLE));

        appUser.getRoles().add(role);

        userRepository.save(appUser);
    }

    @Override
    public CredentialsDto login(LoginDto loginDto) {

        AppUser appUser = userRepository.findByEmail(loginDto.usernameOrEmail())
                .orElseGet(() -> userRepository.findByUsername(loginDto.usernameOrEmail())
                        .orElseThrow(() -> new ApplicationException(ErrorCode.IDENTIFIER_NOT_FOUND, "Username or email not found: " + loginDto.usernameOrEmail())));

        if (!passwordEncoder.matches(loginDto.password(), appUser.getPassword())) {
            throw new ApplicationException(ErrorCode.INVALID_PASSWORD);
        }

        if (!appUser.isEnabled()) {
            throw new ApplicationException(ErrorCode.USER_NOT_ACTIVATED, "User not activated: " + appUser.getEmail());
        }

        return userMapper.toCredentialsDto(appUser);
    }

    @Transactional
    @Override
    public void activateUser(String email) {
        AppUser appUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.EMAIL_NOT_FOUND, "Email not found: " + email));

        if (appUser.isEnabled()) {
            throw new ApplicationException(ErrorCode.USER_ALREADY_ACTIVATED, "User with email " + email + " is already activated.");
        }

        appUser.setEnabled(true);

        userRepository.save(appUser);
    }
}
