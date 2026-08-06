package pl.m22.gamehive.game.dto;

/**
 * Filtry biblioteki dodatków. Kategoria i mechanika działają po WŁASNYCH kolekcjach dodatku —
 * filtrowanie po wartościach efektywnych (z fallbackiem na grę bazową) wymagałoby COALESCE
 * w Criteria API i jest świadomie odłożone do wyszukiwarki (#122).
 */
public record GameExpansionLibraryFilter(Long baseGameId, Long categoryId, Long mechanicId) {
}
