package pl.m22.gamehive.common.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.ErrorCode;

import java.util.List;
import java.util.Set;

public final class RepositoryLookups {

    private RepositoryLookups() {
        throw new IllegalStateException("Utility class");
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
}