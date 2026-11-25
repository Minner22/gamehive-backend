package pl.m22.gamehive.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.m22.gamehive.user.service.AppUserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GH-150: w kontekście nie ma beana {@link AuthenticationProvider}. {@code login} weryfikuje hasło sam
 * ({@code HashedPassword.matches}), a nieużywany {@code DaoAuthenticationProvider} w {@code SecurityConfig}
 * wywoływał przy każdym starcie WARN z {@code InitializeUserDetailsBeanManagerConfigurer} („UserDetailsService
 * beans will not be used…”). Bez niego Spring Security sam składa globalny {@code AuthenticationManager} z jedynego
 * {@link UserDetailsService} (log INFO). Ten {@code UserDetailsService} musi zostać — czyta go
 * {@code UserAuthStateProvider}, a przy okazji wyłącza Bootowe „Using generated security password”.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthenticationManagerConfigTest {

    @Autowired ListableBeanFactory beanFactory;
    @MockitoBean JavaMailSender mailSender;

    @Test
    @DisplayName("GH-150: brak beana AuthenticationProvider (źródło ostrzeżenia Spring Security przy starcie)")
    void noAuthenticationProviderBean() {
        assertThat(beanFactory.getBeanNamesForType(AuthenticationProvider.class)).isEmpty();
    }

    @Test
    @DisplayName("GH-150: dokładnie jeden UserDetailsService — AppUserDetailsService (używa go UserAuthStateProvider)")
    void singleUserDetailsService() {
        assertThat(beanFactory.getBeansOfType(UserDetailsService.class).values())
                .singleElement()
                .isInstanceOf(AppUserDetailsService.class);
    }
}
