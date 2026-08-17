package pl.m22.gamehive.game.search.event;

import pl.m22.gamehive.game.search.dto.TaxonomyDocument;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record TaxonomyIndexEvent(SearchIndexOperation operation, List<TaxonomyDocument> documents,
                                 String documentId) {

    public TaxonomyIndexEvent {

        Objects.requireNonNull(operation, "operation");
        documents = List.copyOf(documents);

        if (operation == SearchIndexOperation.REMOVE) {
            Objects.requireNonNull(documentId, "documentId");
        }
    }

    public static TaxonomyIndexEvent upsert(TaxonomyDocument document) {

        return upsert(List.of(document));
    }

    public static TaxonomyIndexEvent upsert(List<TaxonomyDocument> documents) {

        return new TaxonomyIndexEvent(SearchIndexOperation.UPSERT, documents, null);
    }

    public static TaxonomyIndexEvent remove(String documentId) {

        return new TaxonomyIndexEvent(SearchIndexOperation.REMOVE, List.of(), documentId);
    }

    public String describeTargets() {

        return operation == SearchIndexOperation.REMOVE
                ? documentId
                : documents.stream().map(TaxonomyDocument::id).collect(Collectors.joining(", "));
    }
}
