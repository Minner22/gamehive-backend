package pl.m22.gamehive.collection.model;

/**
 * Status posiadania wpisu w kolekcji. MVP zna wyłącznie {@code OWNED} — enum istnieje po to,
 * by dołożenie {@code WISHLIST} / {@code PLAYED} nie wymagało migracji typu kolumny.
 */
public enum OwnershipStatus {

    OWNED
}
