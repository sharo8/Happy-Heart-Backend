package com.happyhearts.service;

import com.happyhearts.dto.PageData;
import com.happyhearts.dto.response.AuditLogResponse;
import com.happyhearts.model.AuditLog;
import com.happyhearts.repository.AuditLogRepository;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditTrailService {

    private final AuditLogRepository auditLogRepository;
    private final AuditNameResolver auditNameResolver;
    private final AuditTargetResolver auditTargetResolver;

    @Transactional(readOnly = true)
    public PageData<AuditLogResponse> search(
            UUID userId,
            String action,
            Instant from,
            Instant to,
            UUID branchId,
            int page,
            int size
    ) {
        Specification<AuditLog> spec = Specification.allOf(
                withUser(userId),
                withAction(action),
                createdFrom(from),
                createdTo(to),
                withBranch(branchId)
        );
        Page<AuditLog> p = auditLogRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageData.from(p.map(this::map));
    }

    private Specification<AuditLog> withUser(UUID userId) {
        return (root, q, cb) -> userId == null
                ? cb.conjunction()
                : cb.equal(root.join("user", JoinType.LEFT).get("id"), userId);
    }

    private Specification<AuditLog> withAction(String action) {
        return (root, q, cb) -> {
            if (!StringUtils.hasText(action)) {
                return cb.conjunction();
            }
            return cb.equal(root.get("action"), action.trim());
        };
    }

    private Specification<AuditLog> createdFrom(Instant from) {
        return (root, q, cb) -> from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    private Specification<AuditLog> createdTo(Instant to) {
        return (root, q, cb) -> to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    private Specification<AuditLog> withBranch(UUID branchId) {
        return (root, q, cb) -> branchId == null
                ? cb.conjunction()
                : cb.equal(root.join("branch", JoinType.LEFT).get("id"), branchId);
    }

    private AuditLogResponse map(AuditLog a) {
        String email = a.getUser() != null ? a.getUser().getEmail() : null;
        String targetType = a.getTargetType();
        String targetLabel = auditTargetResolver.resolveLabel(targetType, a.getTargetId());
        return AuditLogResponse.builder()
                .id(a.getId())
                .userId(a.getUser() != null ? a.getUser().getId() : null)
                .userEmail(email)
                .userDisplayName(auditNameResolver.resolveDisplayName(email))
                .action(a.getAction())
                .targetId(a.getTargetId())
                .targetType(targetType)
                .targetLabel(targetLabel)
                .details(a.getDetails())
                .ipAddress(a.getIpAddress())
                .userAgent(a.getUserAgent())
                .createdAt(a.getCreatedAt())
                .branchId(a.getBranch() != null ? a.getBranch().getId() : null)
                .branchCode(a.getBranch() != null ? a.getBranch().getCode() : null)
                .branchName(a.getBranch() != null ? a.getBranch().getName() : null)
                .build();
    }
}
