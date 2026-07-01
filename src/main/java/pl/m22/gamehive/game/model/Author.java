package pl.m22.gamehive.game.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.m22.gamehive.common.persistence.LongEntity;

@Entity
@Table(name = "authors", uniqueConstraints = @UniqueConstraint(columnNames = {"first_name", "last_name"}))
@Getter
@NoArgsConstructor
public class Author extends LongEntity {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private Author(String firstName, String lastName) {

        this.firstName = firstName;
        this.lastName = lastName;
    }

    public static Author of(String firstName, String lastName) {

        return new Author(firstName, lastName);
    }
}
