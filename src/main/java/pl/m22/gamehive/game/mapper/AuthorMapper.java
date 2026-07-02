package pl.m22.gamehive.game.mapper;

import org.mapstruct.Mapper;
import pl.m22.gamehive.game.dto.AuthorDto;
import pl.m22.gamehive.game.model.Author;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AuthorMapper {

    AuthorDto toDto(Author author);

    List<AuthorDto> toDtoList(List<Author> authors);
}
