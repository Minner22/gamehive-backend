package pl.m22.gamehive.game.mapper;

import org.mapstruct.Mapper;
import pl.m22.gamehive.game.dto.CategoryDto;
import pl.m22.gamehive.game.model.Category;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryDto toDto(Category category);

    List<CategoryDto> toDtoList(List<Category> categories);
}
