package pl.m22.gamehive.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.persistence.RepositoryLookups;
import pl.m22.gamehive.game.model.Category;
import pl.m22.gamehive.game.model.Mechanic;
import pl.m22.gamehive.game.repository.CategoryRepository;
import pl.m22.gamehive.game.repository.MechanicRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TaxonomyResolver {

    private final CategoryRepository categoryRepository;
    private final MechanicRepository mechanicRepository;

    public List<Category> resolveCategories(List<Long> categoryIds) {

        return RepositoryLookups.findAllOrThrow(categoryRepository, categoryIds, ErrorCode.CATEGORY_NOT_FOUND);
    }

    public List<Mechanic> resolveMechanics(List<Long> mechanicIds) {

        return RepositoryLookups.findAllOrThrow(mechanicRepository, mechanicIds, ErrorCode.MECHANIC_NOT_FOUND);
    }
}