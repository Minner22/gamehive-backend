package pl.m22.gamehive.game.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import pl.m22.gamehive.game.model.Author;
import pl.m22.gamehive.game.model.Publisher;
import pl.m22.gamehive.game.model.TaxonomyTargetType;
import pl.m22.gamehive.game.search.dto.TaxonomyDocument;
import pl.m22.gamehive.game.search.event.TaxonomyIndexEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TaxonomyIndexPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final TaxonomyDocumentFactory documentFactory;

    public void publishUpsert(Publisher publisher) {

        eventPublisher.publishEvent(TaxonomyIndexEvent.upsert(documentFactory.toDocument(publisher)));
    }

    public void publishUpsert(Author author) {

        eventPublisher.publishEvent(TaxonomyIndexEvent.upsert(documentFactory.toDocument(author)));
    }

    public void publishUpsert(Collection<Publisher> publishers, Collection<Author> authors) {

        if (publishers.isEmpty() && authors.isEmpty()) {
            return;
        }

        List<TaxonomyDocument> documents = new ArrayList<>(publishers.size() + authors.size());
        publishers.forEach(publisher -> documents.add(documentFactory.toDocument(publisher)));
        authors.forEach(author -> documents.add(documentFactory.toDocument(author)));

        eventPublisher.publishEvent(TaxonomyIndexEvent.upsert(documents));
    }

    public void publishRemoval(TaxonomyTargetType targetType, Long targetId) {

        eventPublisher.publishEvent(
                TaxonomyIndexEvent.remove(TaxonomyDocumentFactory.documentId(targetType, targetId)));
    }
}
