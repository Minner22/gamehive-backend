package pl.m22.gamehive.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.m22.gamehive.user.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    List<User> findAllUsersByRoles_Name(String role);

    void deleteByEmail(String email);
}
