package pl.m22.gamehive.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.m22.gamehive.user.model.AppUser;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByUsername(String username);

    List<AppUser> findAllUsersByRoles_Name(String role);

    void deleteByEmail(String email);
}
