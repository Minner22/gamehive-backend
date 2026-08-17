package pl.m22.gamehive.game.search.service;

import org.springframework.stereotype.Component;
import pl.m22.gamehive.game.model.Author;
import pl.m22.gamehive.game.model.Publisher;
import pl.m22.gamehive.game.model.TaxonomyTargetType;
import pl.m22.gamehive.game.search.dto.TaxonomyDocument;

@Component
public class TaxonomyDocumentFactory {

    public TaxonomyDocument toDocument(Publisher publisher) {

        return new TaxonomyDocument(
                documentId(TaxonomyTargetType.PUBLISHER, publisher.getId()),
                TaxonomyTargetType.PUBLISHER,
                publisher.getId(),
                publisher.getName(),
                publisher.getStatus());
    }

    public TaxonomyDocument toDocument(Author author) {

        return new TaxonomyDocument(
                documentId(TaxonomyTargetType.AUTHOR, author.getId()),
                TaxonomyTargetType.AUTHOR,
                author.getId(),
                author.getFirstName() + " " + author.getLastName(),
                author.getStatus());
    }

    public static String documentId(TaxonomyTargetType targetType, Long targetId) {

        return switch (targetType) {
            case PUBLISHER -> "publisher-" + targetId;
            case AUTHOR -> "author-" + targetId;
        };
    }
}
