package pl.m22.gamehive.collection.service;

import java.util.UUID;

public interface CollectionCleanupService {

    void removeAllForUser(UUID userId);
}
