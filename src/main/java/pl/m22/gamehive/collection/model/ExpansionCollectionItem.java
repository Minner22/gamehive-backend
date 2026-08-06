package pl.m22.gamehive.collection.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import pl.m22.gamehive.common.persistence.LongEntity;
import pl.m22.gamehive.game.model.GameExpansion;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "expansion_collection_items",
        uniqueConstraints = @UniqueConstraint(name = "uq_expansion_collection_user_expansion",
                columnNames = {"user_id", "expansion_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpansionCollectionItem extends LongEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expansion_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private GameExpansion expansion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OwnershipStatus ownershipStatus;

    public ExpansionCollectionItem(UUID userId, GameExpansion expansion) {

        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.expansion = Objects.requireNonNull(expansion, "expansion must not be null");
        this.ownershipStatus = OwnershipStatus.OWNED;
    }
}
