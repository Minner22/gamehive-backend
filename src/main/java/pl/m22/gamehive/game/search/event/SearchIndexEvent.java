package pl.m22.gamehive.game.search.event;

import pl.m22.gamehive.game.search.dto.GameSearchDocument;

public record SearchIndexEvent(SearchIndexOperation operation, String documentId, GameSearchDocument document) {

    public static SearchIndexEvent upsert(GameSearchDocument document) {

        return new SearchIndexEvent(SearchIndexOperation.UPSERT, document.id(), document);
    }

    public static SearchIndexEvent remove(String documentId) {

        return new SearchIndexEvent(SearchIndexOperation.REMOVE, documentId, null);
    }
}
