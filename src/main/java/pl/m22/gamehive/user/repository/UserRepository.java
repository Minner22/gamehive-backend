package pl.m22.gamehive.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.user.model.AppUser;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<AppUser, UUID> {

    default Optional<AppUser> findByEmail(String email) {
        return findByEmail(new Email(email));
    }

    Optional<AppUser> findByEmail(Email email);

    Optional<AppUser> findByUsername(Username username);

    default boolean existsByEmail(String email) {
        return existsByEmail(new Email(email));
    }

    boolean existsByEmail(Email email);

    boolean existsByUsername(Username username);

    long countByRoles_Name(String roleName);
}