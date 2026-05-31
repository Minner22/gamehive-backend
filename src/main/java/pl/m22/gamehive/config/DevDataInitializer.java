package pl.m22.gamehive.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserRole;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.repository.UserRoleRepository;

import java.util.Set;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        createDevUser("john_doe", "john.doe@example.com", "password123", Set.of("ROLE_ADMIN", "ROLE_USER"));
        createDevUser("jane_smith", "jane.smith@example.com", "password123", Set.of("ROLE_USER"));
        log.info("Dev users initialized");
    }

    private void createDevUser(String username, String email, String password, Set<String> roleNames) {
        if (userRepository.existsByEmail(email)) {
            return;
        }

        Set<UserRole> roles = new java.util.HashSet<>();
        for (String roleName : roleNames) {
            userRoleRepository.findByName(roleName).ifPresent(roles::add);
        }

        AppUser user = AppUser.register(new Username(username), new Email(email), passwordEncoder.encode(password));
        user.activate();

        for (UserRole role : roles) {
            user.assignRole(role);
        }

        userRepository.save(user);
    }
}