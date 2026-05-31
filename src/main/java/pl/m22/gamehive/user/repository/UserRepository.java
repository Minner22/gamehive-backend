package pl.m22.gamehive.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.user.model.AppUser;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    default Optional<AppUser> findByEmail(Email email) {
        return findByEmail(email.value());
    }

    Optional<AppUser> findByUsername(String username);

    default Optional<AppUser> findByUsername(Username username) {
        return findByUsername(username.value());
    }

    List<AppUser> findAllUsersByRoles_Name(String role);

    void deleteByEmail(String email);

    default void deleteByEmail(Email email) {
        deleteByEmail(email.value());
    }

    boolean existsByEmail(String email);

    default boolean existsByEmail(Email email) {
        return existsByEmail(email.value());
    }

    boolean existsByUsername(String username);

    default boolean existsByUsername(Username username) {
        return existsByUsername(username.value());
    }

    long countByRoles_Name(String roleName);
}
