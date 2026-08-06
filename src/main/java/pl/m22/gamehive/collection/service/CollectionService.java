package pl.m22.gamehive.collection.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.m22.gamehive.collection.dto.ExpansionCollectionItemDto;
import pl.m22.gamehive.collection.dto.GameCollectionItemDto;
import pl.m22.gamehive.common.domain.Email;

public interface CollectionService {

    GameCollectionItemDto addGame(Long gameId, Email owner);

    void removeGame(Long gameId, Email owner);

    Page<GameCollectionItemDto> findMyGames(Email owner, Pageable pageable);

    ExpansionCollectionItemDto addExpansion(Long expansionId, Email owner);

    void removeExpansion(Long expansionId, Email owner);

    Page<ExpansionCollectionItemDto> findMyExpansions(Email owner, Pageable pageable);
}
