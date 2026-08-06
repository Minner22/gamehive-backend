package pl.m22.gamehive.collection.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.m22.gamehive.collection.dto.ExpansionCollectionItemDto;
import pl.m22.gamehive.collection.dto.GameCollectionItemDto;
import pl.m22.gamehive.collection.model.ExpansionCollectionItem;
import pl.m22.gamehive.collection.model.GameCollectionItem;
import pl.m22.gamehive.game.mapper.GameExpansionMapper;
import pl.m22.gamehive.game.mapper.GameMapper;

@Mapper(componentModel = "spring", uses = {GameMapper.class, GameExpansionMapper.class})
public interface CollectionMapper {

    @Mapping(target = "addedAt", source = "createdAt")
    GameCollectionItemDto toDto(GameCollectionItem item);

    @Mapping(target = "addedAt", source = "createdAt")
    ExpansionCollectionItemDto toDto(ExpansionCollectionItem item);
}
