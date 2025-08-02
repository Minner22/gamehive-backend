package pl.m22.gamehive.auth.jwt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.m22.gamehive.auth.jwt.model.UserRefreshToken;

import java.util.List;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, Long> {


    boolean existsByJti(String jti);


    List<UserRefreshToken> findByAppUserEmailAndRevokedFalseOrderByCreatedAtAsc(String email);

    Long countByAppUserEmailAndRevokedFalse(String email);

    boolean existsByJtiAndRevokedFalse(String jti);


}
