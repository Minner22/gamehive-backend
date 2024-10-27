package pl.m22.gamehive.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.m22.gamehive.user.model.UserRole;

import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    Optional<UserRole> findByName(String name);
}
