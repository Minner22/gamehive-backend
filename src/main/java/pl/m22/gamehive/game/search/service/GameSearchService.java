package pl.m22.gamehive.game.search.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.m22.gamehive.game.search.dto.GameSearchDocument;
import pl.m22.gamehive.game.search.dto.GameSearchFilter;
import pl.m22.gamehive.game.search.dto.ReindexResultDto;
import pl.m22.gamehive.game.search.dto.SearchResultDto;

public interface GameSearchService {

    void index(GameSearchDocument document);

    void delete(String documentId);

    Page<SearchResultDto> search(String query, GameSearchFilter filter, Pageable pageable);

    ReindexResultDto reindexAll();
}
