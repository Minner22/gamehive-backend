package pl.m22.gamehive.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserProfile;
import pl.m22.gamehive.user.repository.UserRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String USER_ROLE = "ROLE_USER";
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public Optional<CredentialsDto> findCredentialsByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toCredentialsDto);
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

    @Override
    public boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    @Override
    public boolean usernameExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    @Override
    public AppUser findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public AppUser findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public AppUser findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public Page<AppUser> findAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Override
    public UserProfile updateCurrentUserProfile(String email, UserProfileUpdateDto userProfileUpdateDto) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
        UserProfile profile = user.getUserProfile();

        if (profile == null) {
            profile = new UserProfile();
            user.setUserProfile(profile);
        }

        userMapper.updateUserProfileFromDto(userProfileUpdateDto, profile);
        userRepository.save(user);

        return profile;
    }
}
