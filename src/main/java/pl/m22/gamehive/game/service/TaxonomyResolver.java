package pl.m22.gamehive.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.game.model.Category;
import pl.m22.gamehive.game.model.Mechanic;
import pl.m22.gamehive.game.repository.CategoryRepository;
import pl.m22.gamehive.game.repository.MechanicRepository;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TaxonomyResolver {

    private final CategoryRepository categoryRepository;
    private final MechanicRepository mechanicRepository;

    public List<Category> resolveCategories(List<Long> categoryIds) {

        return findAllOrThrow(categoryRepository, categoryIds, ErrorCode.CATEGORY_NOT_FOUND);
    }

    public List<Mechanic> resolveMechanics(List<Long> mechanicIds) {

        return findAllOrThrow(mechanicRepository, mechanicIds, ErrorCode.MECHANIC_NOT_FOUND);
    }

    public static <T> List<T> findAllOrThrow(JpaRepository<T, Long> repository, List<Long> ids, ErrorCode notFound) {

        List<Long> requested = nullSafe(ids);
        List<T> found = repository.findAllById(requested);

        if (found.size() != Set.copyOf(requested).size()) {
            throw new ApplicationException(notFound);
        }

        return found;
    }

    public static <T> List<T> nullSafe(List<T> list) {

        return list == null ? List.of() : list;
    }

    public static boolean isEmpty(List<?> list) {

        return list == null || list.isEmpty();
    }
}
