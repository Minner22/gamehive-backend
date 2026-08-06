package pl.m22.gamehive.game.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.m22.gamehive.common.persistence.ModeratedLongEntity;
import pl.m22.gamehive.common.persistence.ModerationStatus;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "game_expansions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameExpansion extends ModeratedLongEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_game_id", nullable = false, updatable = false)
    private Game baseGame;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    private Integer minPlayers;

    private Integer maxPlayers;

    private Integer playingTimeMinutes;

    private Integer minAge;

    @ManyToMany
    @JoinTable(
            name = "expansion_category",
            joinColumns = @JoinColumn(name = "expansion_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "expansion_mechanic",
            joinColumns = @JoinColumn(name = "expansion_id"),
            inverseJoinColumns = @JoinColumn(name = "mechanic_id")
    )
    private Set<Mechanic> mechanics = new HashSet<>();

    @Builder
    private GameExpansion(Game baseGame, String name, String description, UUID submittedBy,
                          ModerationStatus moderationStatus, Integer minPlayers, Integer maxPlayers,
                          Integer playingTimeMinutes, Integer minAge) {

        super(submittedBy, moderationStatus != null ? moderationStatus : ModerationStatus.PENDING);
        this.baseGame = baseGame;
        this.name = name;
        this.description = description;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.playingTimeMinutes = playingTimeMinutes;
        this.minAge = minAge;
    }

    public void addCategory(Category category) {

        this.categories.add(category);
    }

    public void addMechanic(Mechanic mechanic) {

        this.mechanics.add(mechanic);
    }

    public void updateDetails(String name, String description, Integer minPlayers, Integer maxPlayers,
                              Integer playingTimeMinutes, Integer minAge) {

        this.name = name;
        this.description = description;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.playingTimeMinutes = playingTimeMinutes;
        this.minAge = minAge;
    }

    public void clearAssociations() {

        this.categories.clear();
        this.mechanics.clear();
    }

    public int getEffectiveMinPlayers() {

        return minPlayers != null ? minPlayers : baseGame.getMinPlayers();
    }

    public int getEffectiveMaxPlayers() {

        return maxPlayers != null ? maxPlayers : baseGame.getMaxPlayers();
    }

    public int getEffectivePlayingTimeMinutes() {

        return playingTimeMinutes != null ? playingTimeMinutes : baseGame.getPlayingTimeMinutes();
    }

    public int getEffectiveMinAge() {

        return minAge != null ? minAge : baseGame.getMinAge();
    }

    public Set<Category> getEffectiveCategories() {

        return categories.isEmpty() ? baseGame.getCategories() : categories;
    }

    public Set<Mechanic> getEffectiveMechanics() {

        return mechanics.isEmpty() ? baseGame.getMechanics() : mechanics;
    }
}
