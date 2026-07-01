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
    private PublisherStatus status;

    private Publisher(String name, PublisherStatus status) {

        this.name = name;
        this.status = status;
    }

    public static Publisher of(String name, PublisherStatus status) {

        return new Publisher(name, status);
    }
}
