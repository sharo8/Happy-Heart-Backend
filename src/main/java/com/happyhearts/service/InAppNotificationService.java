package com.happyhearts.service;

import com.happyhearts.dto.response.InAppNotificationResponse;
import com.happyhearts.enums.Role;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.model.InAppNotification;
import com.happyhearts.model.User;
import com.happyhearts.repository.InAppNotificationRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InAppNotificationService {

    private final InAppNotificationRepository inAppNotificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<InAppNotificationResponse> listForUser(UserPrincipal principal, int limit) {
        UUID uid = principal.getId();
        List<InAppNotification> rows = inAppNotificationRepository.findByUser_IdOrderByCreatedAtDesc(uid);
        int max = Math.min(Math.max(limit, 1), 100);
        return rows.stream().limit(max).map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(UserPrincipal principal) {
        return inAppNotificationRepository.countByUser_IdAndReadAtIsNull(principal.getId());
    }

    @Transactional
    public InAppNotificationResponse markRead(UserPrincipal principal, UUID id) {
        InAppNotification n = inAppNotificationRepository
                .findByIdAndUser_Id(id, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notification.not.found"));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            inAppNotificationRepository.save(n);
        }
        return toResponse(n);
    }

    @Transactional
    public void markAllRead(UserPrincipal principal) {
        inAppNotificationRepository.markAllReadForUser(principal.getId(), Instant.now());
    }

    @Transactional
    public void deleteReadForUser(UserPrincipal principal, UUID id) {
        int deleted = inAppNotificationRepository.deleteReadByIdAndUser_Id(id, principal.getId());
        if (deleted <= 0) {
            throw new ResourceNotFoundException("error.notification.not.found");
        }
    }

    @Transactional
    public int deleteAllReadForUser(UserPrincipal principal) {
        return inAppNotificationRepository.deleteAllReadByUser_Id(principal.getId());
    }

    @Transactional
    public void createForUser(UUID userId, String title, String body, String kind) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
        InAppNotification n = InAppNotification.builder()
                .user(user)
                .title(title)
                .body(body)
                .kind(kind)
                .build();
        inAppNotificationRepository.save(n);
    }

    /**
     * Fan-out to all active Super Admins and active Branch Managers for the branch (when non-null).
     */
    @Transactional
    public void notifySuperAdminsAndBranchManagers(UUID branchId, String title, String body, String kind) {
        Set<UUID> sent = new HashSet<>();
        userRepository.findByRoleIn(Collections.singletonList(Role.SUPER_ADMIN), Sort.by("email"))
                .stream()
                .filter(User::isActive)
                .forEach(u -> {
                    if (sent.add(u.getId())) {
                        createForUser(u.getId(), title, body, kind);
                    }
                });
        if (branchId != null) {
            for (Role branchRole : List.of(Role.CENTRAL_COORDINATOR, Role.LEAD_TEACHER)) {
                userRepository.findByBranch_IdAndRole(branchId, branchRole)
                        .stream()
                        .filter(User::isActive)
                        .forEach(u -> {
                            if (sent.add(u.getId())) {
                                createForUser(u.getId(), title, body, kind);
                            }
                        });
            }
        }
    }

    private InAppNotificationResponse toResponse(InAppNotification n) {
        return InAppNotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .body(n.getBody())
                .read(n.getReadAt() != null)
                .kind(n.getKind())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .build();
    }
}
