package pl.m22.gamehive.game.mapper;

import pl.m22.gamehive.game.dto.GameDto;
import pl.m22.gamehive.game.model.Game;

import java.util.List;

public interface GameMapper {

    GameDto toDto(Game game);

    List<GameDto> toDtoList(List<Game> games);
}
