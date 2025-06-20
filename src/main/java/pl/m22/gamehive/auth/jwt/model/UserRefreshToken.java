package pl.m22.gamehive.auth.jwt.model;

import jakarta.persistence.*;
import lombok.*;
import pl.m22.gamehive.common.AbstractEntity;
import pl.m22.gamehive.user.model.AppUser;

import java.time.Instant;

@Entity
@Table(name = "user_refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class UserRefreshToken extends AbstractEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private AppUser appUser;

    @Column(nullable = false, unique = true)
    private String jti;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;
}
