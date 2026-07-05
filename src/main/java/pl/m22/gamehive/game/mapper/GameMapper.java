package pl.m22.gamehive.game.mapper;

import org.mapstruct.Mapper;
import pl.m22.gamehive.game.dto.GameDto;
import pl.m22.gamehive.game.model.Game;

import java.util.List;

@Mapper(componentModel = "spring", uses = {PublisherMapper.class, CategoryMapper.class, MechanicMapper.class, AuthorMapper.class})
public interface GameMapper {

    GameDto toDto(Game game);

    List<GameDto> toDtoList(List<Game> games);
}
