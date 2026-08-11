package pl.m22.gamehive.game.search.service;

import org.springframework.stereotype.Component;
import pl.m22.gamehive.game.search.dto.GameSearchFilter;

import java.util.ArrayList;
import java.util.List;

@Component
public class MeiliFilterBuilder {

    public String[] build(GameSearchFilter filter) {

        List<String> clauses = new ArrayList<>();

        if (filter.targetType() != null) {
            clauses.add("targetType = " + filter.targetType().name());
        }
        equals(clauses, "publisherIds", filter.publisherId());
        equals(clauses, "categoryIds", filter.categoryId());
        equals(clauses, "mechanicIds", filter.mechanicId());
        equals(clauses, "authorIds", filter.authorId());
        equals(clauses, "baseGameId", filter.baseGameId());
        if (filter.players() != null) {
            clauses.add("minPlayers <= " + filter.players());
            clauses.add("maxPlayers >= " + filter.players());
        }
        lessThanOrEqualTo(clauses, "playingTimeMinutes", filter.maxPlayingTime());
        equals(clauses, "yearPublished", filter.yearPublished());
        lessThanOrEqualTo(clauses, "minAge", filter.age());

        return clauses.isEmpty() ? null : clauses.toArray(String[]::new);
    }

    private static void equals(List<String> clauses, String attribute, Number value) {

        if (value != null) {
            clauses.add(attribute + " = " + value);
        }
    }

    private static void lessThanOrEqualTo(List<String> clauses, String attribute, Number value) {

        if (value != null) {
            clauses.add(attribute + " <= " + value);
        }
    }
}
