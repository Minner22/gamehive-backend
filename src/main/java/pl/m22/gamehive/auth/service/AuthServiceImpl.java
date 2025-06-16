package pl.m22.gamehive.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.auth.dto.LoginDto;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.common.exception.*;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserDetails;
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
            throw new EmailAlreadyExistsException(registrationDto.email());
        }

        if (userRepository.existsByUsername(registrationDto.username())) {
            throw new UsernameAlreadyExistsException(registrationDto.username());
        }

        AppUser appUser = userMapper.toUser(registrationDto);
        appUser.setEnabled(false); //TODO: implement email confirmation
        appUser.setPassword(passwordEncoder.encode(registrationDto.password()));
        appUser.setUserDetails(new UserDetails());

        UserRole role = userRoleRepository.findByName(USER_ROLE)
                .orElseThrow(() -> new RoleNotFoundException(USER_ROLE));

        appUser.getRoles().add(role);

        userRepository.save(appUser);
    }

    @Override
    public String login(LoginDto loginDto) {

        AppUser appUser = userRepository.findByEmail(loginDto.usernameOrEmail())
                .orElseGet(() -> userRepository.findByUsername(loginDto.usernameOrEmail())
                        .orElseThrow(() -> new UsernameOrEmailNotFoundException(loginDto.usernameOrEmail())));

        if (!passwordEncoder.matches(loginDto.password(), appUser.getPassword())) {
            throw new InvalidPasswordException();
        }

        return "Mock of the jwt token";
    }
}
