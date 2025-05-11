package pl.m22.gamehive.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.user.dto.UserCredentialsDto;
import pl.m22.gamehive.user.dto.UserRegistrationDto;
import pl.m22.gamehive.user.exception.RoleNotFoundException;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserDetails;
import pl.m22.gamehive.user.model.UserRole;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.repository.UserRoleRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String USER_ROLE = "USER";
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Optional<UserCredentialsDto> findCredentialsByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(UserMapper.INSTANCE::toUserCredentialsDto);
    }

    @Override
    public List<String> findAllUserEmails() {
        return userRepository.findAllUsersByRoles_Name(USER_ROLE).stream()
                .map(AppUser::getEmail)
                .toList();
    }

    @Transactional
    @Override
    public void deleteUserByEmail(String email) {
        userRepository.deleteByEmail(email);
    }

    @Transactional
    @Override
    public void register(UserRegistrationDto registrationDto) {
        AppUser appUser = UserMapper.INSTANCE.toUser(registrationDto);
        Optional<UserRole> userRole = userRoleRepository.findByName(USER_ROLE);
        String passwordHash = passwordEncoder.encode(registrationDto.password());
        appUser.setPassword(passwordHash);
        userRole.ifPresentOrElse(
                role -> appUser.getRoles().add(role),
                () -> {
                    throw new RoleNotFoundException(USER_ROLE);
                }
        );
        appUser.setUserDetails(new UserDetails());
        userRepository.save(appUser);
    }

    @Override
    public boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    @Override
    public boolean usernameExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }
}
