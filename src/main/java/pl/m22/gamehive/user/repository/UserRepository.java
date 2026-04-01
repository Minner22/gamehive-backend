package pl.m22.gamehive.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.m22.gamehive.user.model.AppUser;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmailOrUsername(String email, String username);

    List<AppUser> findAllUsersByRoles_Name(String role);

    void deleteByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}
