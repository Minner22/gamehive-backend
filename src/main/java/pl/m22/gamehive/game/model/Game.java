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
@Table(name = "games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game extends ModeratedLongEntity {

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private int minPlayers;

    @Column(nullable = false)
    private int maxPlayers;

    @Column(nullable = false)
    private int playingTimeMinutes;

    @Column(nullable = false)
    private int yearPublished;

    @Column(nullable = false)
    private int minAge;

    @Column(length = 512)
    private String coverImageUrl;

    @ManyToMany
    @JoinTable(
            name = "game_publisher",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "publisher_id")
    )
    private Set<Publisher> publishers = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "game_category",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "game_mechanic",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "mechanic_id")
    )
    private Set<Mechanic> mechanics = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "game_author",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id")
    )
    private Set<Author> authors = new HashSet<>();

    @Builder
    private Game(String title, String description, UUID submittedBy, ModerationStatus moderationStatus,
                 int minPlayers, int maxPlayers, int playingTimeMinutes,
                 int yearPublished, int minAge, String coverImageUrl) {

        super(submittedBy, moderationStatus != null ? moderationStatus : ModerationStatus.PENDING);
        this.title = title;
        this.description = description;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.playingTimeMinutes = playingTimeMinutes;
        this.yearPublished = yearPublished;
        this.minAge = minAge;
        this.coverImageUrl = coverImageUrl;
    }

    public void addPublisher(Publisher publisher) {

        this.publishers.add(publisher);
    }

    public void addCategory(Category category) {

        this.categories.add(category);
    }

    public void addMechanic(Mechanic mechanic) {

        this.mechanics.add(mechanic);
    }

    public void addAuthor(Author author) {

        this.authors.add(author);
    }

    public void updateDetails(String title, String description, int minPlayers, int maxPlayers,
                              int playingTimeMinutes, int yearPublished, int minAge, String coverImageUrl) {

        this.title = title;
        this.description = description;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.playingTimeMinutes = playingTimeMinutes;
        this.yearPublished = yearPublished;
        this.minAge = minAge;
        this.coverImageUrl = coverImageUrl;
    }

    public void clearAssociations() {

        this.publishers.clear();
        this.categories.clear();
        this.mechanics.clear();
        this.authors.clear();
    }
}
