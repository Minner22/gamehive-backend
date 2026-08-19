package pl.m22.gamehive.game.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.game.repository.AuthorRepository;
import pl.m22.gamehive.game.repository.PublisherRepository;
import pl.m22.gamehive.game.search.dto.TaxonomyDocument;

@Component
@RequiredArgsConstructor
public class TaxonomyDocumentReader {

    private final PublisherRepository publisherRepository;
    private final AuthorRepository authorRepository;
    private final TaxonomyDocumentFactory documentFactory;

    @Transactional(readOnly = true)
    public Page<TaxonomyDocument> readPublishers(Pageable pageable) {

        return publisherRepository.findAll(pageable).map(documentFactory::toDocument);
    }

    @Transactional(readOnly = true)
    public Page<TaxonomyDocument> readAuthors(Pageable pageable) {

        return authorRepository.findAll(pageable).map(documentFactory::toDocument);
    }
}
