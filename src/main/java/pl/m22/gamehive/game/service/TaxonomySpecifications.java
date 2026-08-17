package pl.m22.gamehive.game.service;

import org.springframework.data.jpa.domain.Specification;
import pl.m22.gamehive.common.persistence.Specifications;
import pl.m22.gamehive.game.model.Author;
import pl.m22.gamehive.game.model.Publisher;

public final class TaxonomySpecifications {

    private TaxonomySpecifications() {
        throw new IllegalStateException("Utility class");
    }

    public static Specification<Publisher> publisherNameLike(String query) {

        return Specifications.likeIgnoreCaseIfPresent("name", query);
    }

    public static Specification<Author> authorNameLike(String query) {

        return (root, criteriaQuery, cb) -> Specifications.isBlank(query)
                ? null
                : cb.like(cb.lower(cb.concat(cb.concat(root.<String>get("firstName"), " "),
                        root.<String>get("lastName"))),
                Specifications.likePattern(query), Specifications.LIKE_ESCAPE);
    }
}
