package pl.m22.gamehive.game.search.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.game.search.dto.GameSearchDocument;
import pl.m22.gamehive.game.search.dto.GameSearchFilter;
import pl.m22.gamehive.game.search.dto.ReindexResultDto;
import pl.m22.gamehive.game.search.dto.SearchResultDto;

import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(name = "gamehive.search.enabled", havingValue = "false")
public class NoOpGameSearchService implements GameSearchService {

    @Override
    public void index(List<GameSearchDocument> documents) {

        log.debug("Search disabled - skipping index of {} document(s)", documents.size());
    }

    @Override
    public void delete(String documentId) {

        log.debug("Search disabled - skipping delete of {}", documentId);
    }

    @Override
    public Page<SearchResultDto> search(String query, GameSearchFilter filter, Pageable pageable) {

        log.debug("Search disabled - returning empty page for '{}'", query);
        return Page.empty(pageable);
    }

    @Override
    public ReindexResultDto reindexAll() {

        log.debug("Search disabled - skipping reindex");
        return new ReindexResultDto(0, 0);
    }
}
