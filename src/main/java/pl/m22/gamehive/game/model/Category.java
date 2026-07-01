package pl.m22.gamehive.game.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.m22.gamehive.common.persistence.LongEntity;

@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor
public class Category extends LongEntity {

    @Column(nullable = false, unique = true)
    private String name;

    public Category(String name) {

        this.name = name;
    }

    public static Category of(String name) {

        return new Category(name);
    }
}
