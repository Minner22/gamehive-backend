package pl.m22.gamehive.game.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.m22.gamehive.common.persistence.LongEntity;

@Entity
@Table(name = "publishers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Publisher extends LongEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaxonomyStatus status;

    private Publisher(String name, TaxonomyStatus status) {

        this.name = name;
        this.status = status;
    }

    public static Publisher of(String name, TaxonomyStatus status) {

        return new Publisher(name, status);
    }

    public void approve() {

        this.status = TaxonomyStatus.APPROVED;
    }
}
