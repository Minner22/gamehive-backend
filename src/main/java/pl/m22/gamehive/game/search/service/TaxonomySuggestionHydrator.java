package pl.m22.gamehive.game.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.game.dto.AuthorDto;
import pl.m22.gamehive.game.dto.PublisherDto;
import pl.m22.gamehive.game.mapper.AuthorMapper;
import pl.m22.gamehive.game.mapper.PublisherMapper;
import pl.m22.gamehive.game.model.Author;
import pl.m22.gamehive.game.model.Publisher;
import pl.m22.gamehive.game.repository.AuthorRepository;
import pl.m22.gamehive.game.repository.PublisherRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TaxonomySuggestionHydrator {

    private final PublisherRepository publisherRepository;
    private final AuthorRepository authorRepository;
    private final PublisherMapper publisherMapper;
    private final AuthorMapper authorMapper;

    @Transactional(readOnly = true)
    public List<PublisherDto> hydratePublishers(List<Long> targetIds) {

        return hydrate(targetIds, publisherRepository::findAllById, Publisher::getId, publisherMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<AuthorDto> hydrateAuthors(List<Long> targetIds) {

        return hydrate(targetIds, authorRepository::findAllById, Author::getId, authorMapper::toDto);
    }

    private static <E, D> List<D> hydrate(List<Long> targetIds,
                                          Function<List<Long>, List<E>> finder,
                                          Function<E, Long> id,
                                          Function<E, D> toDto) {

        if (targetIds.isEmpty()) {
            return List.of();
        }

        Map<Long, D> byId = finder.apply(targetIds).stream().collect(Collectors.toMap(id, toDto));

        return targetIds.stream().distinct().map(byId::get).filter(Objects::nonNull).toList();
    }
}
