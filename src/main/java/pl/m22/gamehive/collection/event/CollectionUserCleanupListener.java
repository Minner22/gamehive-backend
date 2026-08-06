package pl.m22.gamehive.collection.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.m22.gamehive.collection.service.CollectionCleanupService;
import pl.m22.gamehive.user.event.UserDeletedEvent;

@Component
@RequiredArgsConstructor
public class CollectionUserCleanupListener {

    private final CollectionCleanupService collectionCleanupService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserDeleted(UserDeletedEvent event) {

        collectionCleanupService.removeAllForUser(event.userId());
    }
}
