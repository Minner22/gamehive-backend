package pl.m22.gamehive.collection.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.collection.repository.ExpansionCollectionItemRepository;
import pl.m22.gamehive.collection.repository.GameCollectionItemRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollectionCleanupServiceImpl implements CollectionCleanupService {

    private final GameCollectionItemRepository gameCollectionRepository;
    private final ExpansionCollectionItemRepository expansionCollectionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void removeAllForUser(UUID userId) {

        gameCollectionRepository.deleteByUserId(userId);
        expansionCollectionRepository.deleteByUserId(userId);
    }
}
