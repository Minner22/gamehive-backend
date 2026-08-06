package pl.m22.gamehive.game.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.m22.gamehive.game.dto.GameExpansionDto;
import pl.m22.gamehive.game.dto.GameExpansionModerationDto;
import pl.m22.gamehive.game.model.GameExpansion;

@Mapper(componentModel = "spring", uses = {CategoryMapper.class, MechanicMapper.class})
public interface GameExpansionMapper {

    @Mapping(target = "baseGameId", source = "baseGame.id")
    @Mapping(target = "baseGameTitle", source = "baseGame.title")
    GameExpansionDto toDto(GameExpansion expansion);

    @Mapping(target = "baseGameId", source = "baseGame.id")
    @Mapping(target = "baseGameTitle", source = "baseGame.title")
    GameExpansionModerationDto toModerationDto(GameExpansion expansion);
}
