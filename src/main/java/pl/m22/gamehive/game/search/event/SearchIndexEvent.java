package pl.m22.gamehive.game.search.event;

import pl.m22.gamehive.game.search.dto.GameSearchDocument;

import java.util.List;
import java.util.stream.Collectors;

public record SearchIndexEvent(SearchIndexOperation operation, List<GameSearchDocument> documents, String documentId) {

    public static SearchIndexEvent upsert(GameSearchDocument document) {

        return upsert(List.of(document));
    }

    public static SearchIndexEvent upsert(List<GameSearchDocument> documents) {

        return new SearchIndexEvent(SearchIndexOperation.UPSERT, List.copyOf(documents), null);
    }

    public static SearchIndexEvent remove(String documentId) {

        return new SearchIndexEvent(SearchIndexOperation.REMOVE, List.of(), documentId);
    }

    public String describeTargets() {

        return operation == SearchIndexOperation.REMOVE
                ? documentId
                : documents.stream().map(GameSearchDocument::id).collect(Collectors.joining(", "));
    }
}
