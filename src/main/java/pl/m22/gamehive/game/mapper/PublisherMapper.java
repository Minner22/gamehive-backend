package pl.m22.gamehive.game.mapper;

import org.mapstruct.Mapper;
import pl.m22.gamehive.game.dto.PublisherDto;
import pl.m22.gamehive.game.model.Publisher;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PublisherMapper {

    PublisherDto toDto(Publisher publisher);

    List<PublisherDto> toDtoList(List<Publisher> publishers);
}
