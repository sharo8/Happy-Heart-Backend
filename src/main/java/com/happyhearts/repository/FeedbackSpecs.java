package com.happyhearts.repository;

import com.happyhearts.enums.FeedbackVisibility;
import com.happyhearts.enums.Role;
import com.happyhearts.model.Feedback;
import com.happyhearts.security.UserPrincipal;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FeedbackSpecs {

    private FeedbackSpecs() {
    }

    public static Specification<Feedback> forPrincipal(UserPrincipal principal) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            return switch (principal.getRole()) {
                case SUPER_ADMIN -> cb.conjunction();
                case GENERAL_MANAGER_PEDAGOGIQUE -> cb.conjunction();
                case CENTRAL_COORDINATOR, LEAD_TEACHER -> {
                    UUID bid = principal.getBranchId();
                    if (bid == null) {
                        yield cb.disjunction();
                    }
                    Predicate sameBranch = cb.equal(root.get("branch").get("id"), bid);
                    Predicate involved = cb.or(
                            cb.equal(root.get("toUser").get("id"), principal.getId()),
                            cb.equal(root.get("fromUser").get("id"), principal.getId())
                    );
                    Predicate shared = root.get("visibility").in(FeedbackVisibility.SUPERIORS, FeedbackVisibility.PUBLIC);
                    yield cb.or(
                            cb.and(sameBranch, shared),
                            involved,
                            cb.and(cb.isNull(root.get("branch")), involved)
                    );
                }
                case ASSISTANT, COOK, CLEANER, TEACHER ->
                        cb.equal(root.get("toUser").get("id"), principal.getId());
            };
        };
    }

    public static Specification<Feedback> search(String q) {
        if (!StringUtils.hasText(q)) {
            return (root, query, cb) -> cb.conjunction();
        }
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            var from = root.join("fromUser", JoinType.LEFT);
            var to = root.join("toUser", JoinType.LEFT);
            List<Predicate> parts = new ArrayList<>();
            parts.add(cb.like(cb.lower(root.get("content")), like));
            parts.add(cb.like(cb.lower(from.get("email")), like));
            parts.add(cb.like(cb.lower(to.get("email")), like));
            parts.add(cb.like(cb.lower(from.get("firstName")), like));
            parts.add(cb.like(cb.lower(from.get("lastName")), like));
            parts.add(cb.like(cb.lower(to.get("firstName")), like));
            parts.add(cb.like(cb.lower(to.get("lastName")), like));
            parts.add(cb.like(cb.lower(root.get("type").as(String.class)), like));
            parts.add(cb.like(cb.lower(root.get("visibility").as(String.class)), like));
            return cb.or(parts.toArray(Predicate[]::new));
        };
    }

    public static boolean canMutate(UserPrincipal principal, Feedback feedback) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return true;
        }
        return feedback.getFromUser() != null
                && feedback.getFromUser().getId().equals(principal.getId());
    }
}
