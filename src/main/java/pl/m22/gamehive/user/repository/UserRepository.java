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

    default Optional<AppUser> findByEmail(String email) {
        return findByEmail(new Email(email));
    }

    Optional<AppUser> findByEmail(Email email);

    default Optional<AppUser> findByUsername(String username) {
        return findByUsername(new Username(username));
    }

    Optional<AppUser> findByUsername(Username username);

    List<AppUser> findAllUsersByRoles_Name(String role);

    default void deleteByEmail(String email) {
        deleteByEmail(new Email(email));
    }

    void deleteByEmail(Email email);

    default boolean existsByEmail(String email) {
        return existsByEmail(new Email(email));
    }

    boolean existsByEmail(Email email);

    default boolean existsByUsername(String username) {
        return existsByUsername(new Username(username));
    }

    boolean existsByUsername(Username username);

    long countByRoles_Name(String roleName);
}
