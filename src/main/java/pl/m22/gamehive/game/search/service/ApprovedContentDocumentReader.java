package pl.m22.gamehive.game.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.game.search.dto.GameSearchDocument;

@Component
@RequiredArgsConstructor
public class ApprovedContentDocumentReader {

    private final GameRepository gameRepository;
    private final GameExpansionRepository expansionRepository;
    private final GameSearchDocumentFactory documentFactory;

    @Transactional(readOnly = true)
    public Page<GameSearchDocument> readGames(Pageable pageable) {

        return gameRepository.findByModerationStatus(ModerationStatus.APPROVED, pageable)
                .map(documentFactory::toDocument);
    }

    @Transactional(readOnly = true)
    public Page<GameSearchDocument> readExpansions(Pageable pageable) {

        return expansionRepository.findByModerationStatus(ModerationStatus.APPROVED, pageable)
                .map(documentFactory::toDocument);
    }
}
