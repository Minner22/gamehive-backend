package pl.m22.gamehive.game.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.m22.gamehive.common.persistence.LongEntity;

@Entity
@Table(name = "mechanics")
@Getter
@NoArgsConstructor
public class Mechanic extends LongEntity {

    @Column(nullable = false, unique = true)
    private String name;

    private Mechanic(String name) {

        this.name = name;
    }

    public static Mechanic of(String name) {

        return new Mechanic(name);
    }
}
