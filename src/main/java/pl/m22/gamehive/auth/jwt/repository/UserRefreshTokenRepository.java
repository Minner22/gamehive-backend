package pl.m22.gamehive.auth.jwt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.m22.gamehive.auth.jwt.model.UserRefreshToken;
import pl.m22.gamehive.user.model.AppUser;

import java.util.List;
import java.util.Optional;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, Long> {


    boolean existsByJti(String jti);


    List<UserRefreshToken> findByAppUserEmailAndRevokedFalseOrderByCreatedAtAsc(String email);

    Long countByAppUserEmailAndRevokedFalse(String email);

    boolean existsByJtiAndRevokedFalse(String jti);


}
