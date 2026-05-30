package pl.m22.gamehive.auth.jwt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.config.CacheConfig;
import pl.m22.gamehive.user.service.AppUserDetailsService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAuthStateProvider {

    private final AppUserDetailsService appUserDetailsService;
    private final RedisSessionEpochStore redisSessionEpochStore;

    @Cacheable(value = CacheConfig.USER_AUTH_STATE, key = "#email")
    public UserAuthState getAuthState(String email) {

        // wołane w obrębie żądania (open-in-view ON) -> lazy role załadują się tu; do cache trafia
        // już zmaterializowana lista, więc późniejszy odczyt nie dotyka sesji Hibernate.
        UserDetails details = appUserDetailsService.loadUserByUsername(email);
        List<GrantedAuthority> authorities = List.copyOf(details.getAuthorities());
        Long invalidAfter = redisSessionEpochStore.getInvalidAfter(email);

        return new UserAuthState(details.isEnabled(), authorities, invalidAfter);
    }
}
