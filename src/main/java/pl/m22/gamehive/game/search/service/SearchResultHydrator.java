package pl.m22.gamehive.game.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.mapper.GameExpansionMapper;
import pl.m22.gamehive.game.mapper.GameMapper;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.game.search.dto.SearchHitRef;
import pl.m22.gamehive.game.search.dto.SearchResultDto;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SearchResultHydrator {

    private final GameRepository gameRepository;
    private final GameExpansionRepository expansionRepository;
    private final GameMapper gameMapper;
    private final GameExpansionMapper expansionMapper;

    @Transactional(readOnly = true)
    public List<SearchResultDto> hydrate(List<SearchHitRef> hits) {

        if (hits.isEmpty()) {
            return List.of();
        }

        Map<Long, SearchResultDto> games = approvedById(
                gameRepository.findAllById(targetIds(hits, ContentModerationTargetType.GAME)),
                Game::getId, Game::getModerationStatus, game -> SearchResultDto.of(gameMapper.toDto(game)));

        Map<Long, SearchResultDto> expansions = approvedById(
                expansionRepository.findAllById(targetIds(hits, ContentModerationTargetType.EXPANSION)),
                GameExpansion::getId, GameExpansion::getModerationStatus,
                expansion -> SearchResultDto.of(expansionMapper.toDto(expansion)));

        return hits.stream()
                .map(hit -> switch (hit.targetType()) {
                    case GAME -> games.get(hit.targetId());
                    case EXPANSION -> expansions.get(hit.targetId());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<Long> targetIds(List<SearchHitRef> hits, ContentModerationTargetType targetType) {

        return hits.stream()
                .filter(hit -> hit.targetType() == targetType)
                .map(SearchHitRef::targetId)
                .distinct()
                .toList();
    }

    private static <T> Map<Long, SearchResultDto> approvedById(List<T> entities,
                                                              Function<T, Long> id,
                                                              Function<T, ModerationStatus> status,
                                                              Function<T, SearchResultDto> toResult) {

        return entities.stream()
                .filter(entity -> status.apply(entity) == ModerationStatus.APPROVED)
                .collect(Collectors.toMap(id, toResult));
    }
}
