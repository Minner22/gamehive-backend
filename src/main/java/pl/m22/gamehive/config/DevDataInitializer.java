package pl.m22.gamehive.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserProfile;
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
    public void run(ApplicationArguments args) {
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

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setUserProfile(new UserProfile());
        user.setRoles(roles);

        userRepository.save(user);
    }
}