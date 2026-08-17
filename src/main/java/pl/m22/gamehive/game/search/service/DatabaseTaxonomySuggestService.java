package pl.m22.gamehive.game.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.game.dto.AuthorDto;
import pl.m22.gamehive.game.dto.PublisherDto;
import pl.m22.gamehive.game.mapper.AuthorMapper;
import pl.m22.gamehive.game.mapper.PublisherMapper;
import pl.m22.gamehive.game.repository.AuthorRepository;
import pl.m22.gamehive.game.repository.PublisherRepository;
import pl.m22.gamehive.game.search.dto.TaxonomyDocument;
import pl.m22.gamehive.game.search.dto.TaxonomyReindexCounts;
import pl.m22.gamehive.game.service.TaxonomySpecifications;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gamehive.search.enabled", havingValue = "false")
public class DatabaseTaxonomySuggestService implements TaxonomySuggestService {

    private final PublisherRepository publisherRepository;
    private final AuthorRepository authorRepository;
    private final PublisherMapper publisherMapper;
    private final AuthorMapper authorMapper;

    @Transactional(readOnly = true)
    @Override
    public List<PublisherDto> suggestPublishers(String query, int limit) {

        return publisherMapper.toDtoList(publisherRepository
                .findAll(TaxonomySpecifications.publisherNameLike(query),
                        PageRequest.of(0, limit, Sort.by("name")))
                .getContent());
    }

    @Transactional(readOnly = true)
    @Override
    public List<AuthorDto> suggestAuthors(String query, int limit) {

        return authorMapper.toDtoList(authorRepository
                .findAll(TaxonomySpecifications.authorNameLike(query),
                        PageRequest.of(0, limit, Sort.by("lastName", "firstName")))
                .getContent());
    }

    @Override
    public void index(List<TaxonomyDocument> documents) {

        log.debug("Search disabled - skipping taxonomy index of {} document(s)", documents.size());
    }

    @Override
    public void delete(String documentId) {

        log.debug("Search disabled - skipping taxonomy delete of {}", documentId);
    }

    @Override
    public TaxonomyReindexCounts reindexAll() {

        log.debug("Search disabled - skipping taxonomy reindex");

        return new TaxonomyReindexCounts(0, 0);
    }
}
