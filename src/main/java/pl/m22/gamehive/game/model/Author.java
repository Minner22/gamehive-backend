package pl.m22.gamehive.game.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.m22.gamehive.common.persistence.LongEntity;

@Entity
@Table(name = "authors", uniqueConstraints = @UniqueConstraint(columnNames = {"first_name", "last_name"}))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Author extends LongEntity {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaxonomyStatus status;


    private Author(String firstName, String lastName, TaxonomyStatus status) {

        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
    }

    public static Author of(String firstName, String lastName, TaxonomyStatus status) {

        return new Author(firstName, lastName, status);
    }

    public void rename(String firstName, String lastName) {

        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void approve() {

        this.status = TaxonomyStatus.APPROVED;
    }
}
