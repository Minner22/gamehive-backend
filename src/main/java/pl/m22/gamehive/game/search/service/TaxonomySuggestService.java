package pl.m22.gamehive.game.search.service;

import pl.m22.gamehive.game.dto.AuthorDto;
import pl.m22.gamehive.game.dto.PublisherDto;
import pl.m22.gamehive.game.search.dto.TaxonomyDocument;
import pl.m22.gamehive.game.search.dto.TaxonomyReindexCounts;

import java.util.List;

public interface TaxonomySuggestService {

    List<PublisherDto> suggestPublishers(String query, int limit);

    List<AuthorDto> suggestAuthors(String query, int limit);

    void index(List<TaxonomyDocument> documents);

    void delete(String documentId);

    TaxonomyReindexCounts reindexAll();
}
