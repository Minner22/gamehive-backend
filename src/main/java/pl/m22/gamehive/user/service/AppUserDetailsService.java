package pl.m22.gamehive.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.common.exception.UsernameOrEmailNotFoundException;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.util.AppUserDetails;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = userRepository.findByEmail(username)
                .orElseGet(() -> userRepository.findByUsername(username)
                        .orElseThrow(() -> new UsernameOrEmailNotFoundException(username)));
        return new AppUserDetails(appUser);
    }
}
