package pl.m22.gamehive.auth.jwt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pl.m22.gamehive.auth.jwt.model.UserRefreshToken;

import java.util.List;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, Long> {

    List<UserRefreshToken> findByAppUserEmailAndRevokedFalseOrderByCreatedAtAsc(String email);

    Long countByAppUserEmailAndRevokedFalse(String email);

    boolean existsByJtiAndRevokedFalse(String jti);

    @Modifying
    @Query("UPDATE UserRefreshToken t SET t.revoked = true WHERE t.appUser.email = :email AND t.revoked = false")
    void revokeAllByUserEmail(String email);

}
