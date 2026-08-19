package pl.m22.gamehive.game.search.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.search.dto.ContentReindexCounts;
import pl.m22.gamehive.game.search.dto.GameSearchDocument;
import pl.m22.gamehive.game.search.dto.GameSearchFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class NoOpGameSearchServiceTest {

    private final NoOpGameSearchService service = new NoOpGameSearchService();

    @Test
    @DisplayName("indeksowanie i usuwanie są ciche — brak Meili nie może wysadzić akcji moderacyjnej")
    void indexingIsSilent() {
        GameSearchDocument document = new GameSearchDocument("game-1", ContentModerationTargetType.GAME, 1L,
                "Agricola", "Opis.", null, List.of(1L), List.of(1L), List.of(), List.of(),
                1, 4, 120, 2007, 12, null);

        assertThatNoException().isThrownBy(() -> service.index(List.of(document)));
        assertThatNoException().isThrownBy(() -> service.index(List.of()));
        assertThatNoException().isThrownBy(() -> service.delete("game-1"));
    }

    @Test
    @DisplayName("wyszukiwanie zwraca pustą stronę o żądanym rozmiarze (endpoint odpowiada 200, nie 5xx)")
    void searchReturnsEmptyPage() {
        GameSearchFilter filter = new GameSearchFilter(null, null, null, null, null, null, null, null, null, null);

        var page = service.search("agricola", filter, PageRequest.of(0, 20));

        assertThat(page).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("reindeks na fallbacku oddaje zerowe liczniki, bez próby połączenia")
    void reindexReturnsZeroCounts() {
        assertThat(service.reindexAll()).isEqualTo(new ContentReindexCounts(0, 0));
    }
}
