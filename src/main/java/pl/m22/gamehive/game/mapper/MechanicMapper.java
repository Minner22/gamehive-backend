package pl.m22.gamehive.game.mapper;

import org.mapstruct.Mapper;
import pl.m22.gamehive.game.dto.MechanicDto;
import pl.m22.gamehive.game.model.Mechanic;

@Mapper(componentModel = "spring")
public interface MechanicMapper {

    MechanicDto toDto(Mechanic mechanic);
}
