package pl.m22.gamehive.user.service;

import org.springframework.stereotype.Service;
import pl.m22.gamehive.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}
