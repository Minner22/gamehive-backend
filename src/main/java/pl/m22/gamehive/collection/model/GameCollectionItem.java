package pl.m22.gamehive.collection.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import pl.m22.gamehive.common.persistence.LongEntity;
import pl.m22.gamehive.game.model.Game;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "game_collection_items",
        uniqueConstraints = @UniqueConstraint(name = "uq_game_collection_user_game",
                columnNames = {"user_id", "game_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameCollectionItem extends LongEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Game game;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OwnershipStatus ownershipStatus;

    public GameCollectionItem(UUID userId, Game game) {

        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.game = Objects.requireNonNull(game, "game must not be null");
        this.ownershipStatus = OwnershipStatus.OWNED;
    }
}
