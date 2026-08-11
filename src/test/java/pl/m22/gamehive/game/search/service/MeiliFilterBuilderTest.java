package pl.m22.gamehive.game.search.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.search.dto.GameSearchFilter;

import static org.assertj.core.api.Assertions.assertThat;

class MeiliFilterBuilderTest {

    private final MeiliFilterBuilder builder = new MeiliFilterBuilder();

    private static GameSearchFilter filter(ContentModerationTargetType targetType, Long publisherId, Long categoryId,
                                           Long mechanicId, Long authorId, Long baseGameId, Integer players,
                                           Integer maxPlayingTime, Integer yearPublished, Integer age) {

        return new GameSearchFilter(targetType, publisherId, categoryId, mechanicId, authorId, baseGameId,
                players, maxPlayingTime, yearPublished, age);
    }

    private static GameSearchFilter empty() {

        return filter(null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("pusty filtr -> null (Meili nie dostaje wyrażenia, nie puste [])")
    void emptyFilter_producesNoExpression() {
        assertThat(builder.build(empty())).isNull();
    }

    @Test
    @DisplayName("filtry po id -> równość po atrybutach dokumentu; każdy warunek osobną klauzulą (AND)")
    void idFilters_mapToEquality() {
        String[] expression = builder.build(
                filter(ContentModerationTargetType.GAME, 1L, 2L, 3L, 4L, 7L, null, null, null, null));

        assertThat(expression).containsExactly(
                "targetType = GAME",
                "publisherIds = 1",
                "categoryIds = 2",
                "mechanicIds = 3",
                "authorIds = 4",
                "baseGameId = 7");
    }

    @Test
    @DisplayName("players=3 -> dwustronny zakres minPlayers <= 3 AND maxPlayers >= 3 (jak GameSpecifications)")
    void players_mapsToTwoSidedRange() {
        assertThat(builder.build(filter(null, null, null, null, null, null, 3, null, null, null)))
                .containsExactly("minPlayers <= 3", "maxPlayers >= 3");
    }

    @Test
    @DisplayName("czas/rok/wiek -> <=, =, <= (te same semantyki co filtry biblioteki GH-119)")
    void rangeFilters_matchLibrarySemantics() {
        assertThat(builder.build(filter(null, null, null, null, null, null, null, 120, 2007, 12)))
                .containsExactly("playingTimeMinutes <= 120", "yearPublished = 2007", "minAge <= 12");
    }

    @Test
    @DisplayName("targetType=EXPANSION -> filtr po nazwie stałej enuma (tak trafia do indeksu)")
    void targetTypeExpansion_usesEnumName() {
        assertThat(builder.build(filter(ContentModerationTargetType.EXPANSION,
                null, null, null, null, null, null, null, null, null)))
                .containsExactly("targetType = EXPANSION");
    }
}
