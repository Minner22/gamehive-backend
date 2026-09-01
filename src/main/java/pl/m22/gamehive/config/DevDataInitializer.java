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
import pl.m22.gamehive.common.domain.HashedPassword;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserRole;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.repository.UserRoleRepository;

import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final DevGameSeeder devGameSeeder;

    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        AppUser admin = createDevUser("john_doe", "john.doe@example.com", "password123", Set.of("ROLE_ADMIN", "ROLE_USER"));
        AppUser user = createDevUser("jane_smith", "jane.smith@example.com", "password123", Set.of("ROLE_USER"));
        log.info("Dev users initialized");

        // wołane wprost, a nie jako osobny ApplicationRunner z @Order: gry potrzebują UUID-ów
        // powyższych kont, więc zależność ma być widoczna w kodzie, a nie ukryta w adnotacji
        devGameSeeder.seed(admin.getId(), user.getId());
    }

    private AppUser createDevUser(String username, String email, String password, Set<String> roleNames) {
        Optional<AppUser> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }

        Set<UserRole> roles = new java.util.HashSet<>();
        for (String roleName : roleNames) {
            userRoleRepository.findByName(roleName).ifPresent(roles::add);
        }

        AppUser user = AppUser.register(new Username(username), new Email(email), HashedPassword.fromRaw(password, passwordEncoder));
        user.activate();

        for (UserRole role : roles) {
            user.assignRole(role);
        }

        return userRepository.save(user);
    }
}