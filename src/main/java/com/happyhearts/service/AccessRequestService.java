package com.happyhearts.service;

import com.happyhearts.dto.request.ApproveAccessRequestRequest;
import com.happyhearts.dto.request.CreateDashboardUserRequest;
import com.happyhearts.dto.request.RejectAccessRequestRequest;
import com.happyhearts.dto.request.SubmitAccessRequestRequest;
import com.happyhearts.dto.response.AccessRequestResponse;
import com.happyhearts.dto.response.CreatedUserResponse;
import com.happyhearts.enums.AccessRequestStatus;
import com.happyhearts.enums.Language;
import com.happyhearts.enums.Role;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.model.AccessRequest;
import com.happyhearts.model.Branch;
import com.happyhearts.model.User;
import com.happyhearts.repository.AccessRequestRepository;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessRequestService {

    private final AccessRequestRepository accessRequestRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final DashboardUserService dashboardUserService;
    private final EmailService emailService;
    private final InAppNotificationService inAppNotificationService;
    private final AuditService auditService;

    @Transactional
    public AccessRequestResponse submit(SubmitAccessRequestRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("error.access.request.email.exists");
        }
        if (accessRequestRepository.existsByEmailIgnoreCaseAndStatus(email, AccessRequestStatus.PENDING)) {
            throw new BusinessException("error.access.request.already.pending");
        }

        AccessRequest row = AccessRequest.builder()
                .email(email)
                .firstName(trim(request.getFirstName()))
                .lastName(trim(request.getLastName()))
                .phone(trim(request.getPhone()))
                .message(request.getMessage().trim())
                .preferredLanguage(request.getPreferredLanguage() != null ? request.getPreferredLanguage() : Language.EN)
                .status(AccessRequestStatus.PENDING)
                .build();
        row = accessRequestRepository.save(row);

        try {
            inAppNotificationService.notifySuperAdminsAndBranchManagers(
                    null,
                    "New access request",
                    email + " · " + abbreviate(row.getMessage(), 120),
                    "ACCESS_REQUEST");
        } catch (Exception ex) {
            log.warn("Could not notify admins about access request: {}", ex.getMessage());
        }

        return toResponse(row);
    }

    @Transactional(readOnly = true)
    public List<AccessRequestResponse> listAll(String statusFilter) {
        List<AccessRequest> rows;
        if (StringUtils.hasText(statusFilter) && !"ALL".equalsIgnoreCase(statusFilter)) {
            AccessRequestStatus status = AccessRequestStatus.valueOf(statusFilter.toUpperCase());
            rows = accessRequestRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            rows = accessRequestRepository.findAllByOrderByCreatedAtDesc();
        }
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional
    public AccessRequestResponse approve(UserPrincipal admin, UUID id, ApproveAccessRequestRequest request) {
        AccessRequest row = accessRequestRepository.findByIdAndStatus(id, AccessRequestStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("error.access.request.not.found"));

        assertRoleAllowedForReviewer(admin, request.getRole());

        CreateDashboardUserRequest create = new CreateDashboardUserRequest();
        create.setEmail(row.getEmail());
        create.setFirstName(row.getFirstName());
        create.setLastName(row.getLastName());
        create.setRole(request.getRole());
        create.setPreferredLanguage(request.getPreferredLanguage());
        create.setBranchId(request.getBranchId());

        CreatedUserResponse created = dashboardUserService.createDashboardUser(admin, create);
        User createdUser = userRepository.findById(created.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));

        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId()).orElse(null);
        }

        User reviewer = userRepository.findById(admin.getId()).orElse(null);
        row.setStatus(AccessRequestStatus.APPROVED);
        row.setAdminMessage(trim(request.getAdminMessage()));
        row.setAssignedRole(request.getRole());
        row.setAssignedBranch(branch);
        row.setReviewedBy(reviewer);
        row.setReviewedAt(Instant.now());
        row.setCreatedUser(createdUser);
        row = accessRequestRepository.save(row);

        try {
            emailService.sendAccessRequestDecision(
                    row.getEmail(),
                    row.getFirstName(),
                    request.getPreferredLanguage(),
                    true,
                    request.getAdminMessage(),
                    request.getRole().name()
            );
        } catch (Exception ex) {
            log.warn("Approval email could not be sent to {}: {}", row.getEmail(), ex.getMessage());
        }

        auditService.log(admin, "ACCESS_REQUEST_APPROVED", row.getId(), "access_request", null, null);
        return toResponse(row);
    }

    @Transactional
    public AccessRequestResponse reject(UserPrincipal admin, UUID id, RejectAccessRequestRequest request) {
        AccessRequest row = accessRequestRepository.findByIdAndStatus(id, AccessRequestStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("error.access.request.not.found"));

        User reviewer = userRepository.findById(admin.getId()).orElse(null);
        row.setStatus(AccessRequestStatus.REJECTED);
        row.setAdminMessage(request.getAdminMessage().trim());
        row.setReviewedBy(reviewer);
        row.setReviewedAt(Instant.now());
        row = accessRequestRepository.save(row);

        try {
            emailService.sendAccessRequestDecision(
                    row.getEmail(),
                    row.getFirstName(),
                    row.getPreferredLanguage() != null ? row.getPreferredLanguage() : Language.EN,
                    false,
                    request.getAdminMessage(),
                    null
            );
        } catch (Exception ex) {
            log.warn("Rejection email could not be sent to {}: {}", row.getEmail(), ex.getMessage());
        }

        auditService.log(admin, "ACCESS_REQUEST_REJECTED", row.getId(), "access_request", null, null);
        return toResponse(row);
    }

    private void assertRoleAllowedForReviewer(UserPrincipal admin, Role role) {
        if (admin.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE && role == Role.SUPER_ADMIN) {
            throw new BusinessException("error.user.gmp.cannot.assign.superadmin");
        }
    }

    private AccessRequestResponse toResponse(AccessRequest row) {
        return AccessRequestResponse.builder()
                .id(row.getId())
                .email(row.getEmail())
                .firstName(row.getFirstName())
                .lastName(row.getLastName())
                .phone(row.getPhone())
                .message(row.getMessage())
                .preferredLanguage(row.getPreferredLanguage())
                .status(row.getStatus())
                .adminMessage(row.getAdminMessage())
                .assignedRole(row.getAssignedRole())
                .assignedBranchId(row.getAssignedBranch() != null ? row.getAssignedBranch().getId() : null)
                .assignedBranchCode(row.getAssignedBranch() != null ? row.getAssignedBranch().getCode() : null)
                .assignedBranchName(row.getAssignedBranch() != null ? row.getAssignedBranch().getName() : null)
                .reviewedByName(row.getReviewedBy() != null
                        ? ((row.getReviewedBy().getFirstName() != null ? row.getReviewedBy().getFirstName() : "")
                        + " " + (row.getReviewedBy().getLastName() != null ? row.getReviewedBy().getLastName() : "")).trim()
                        : null)
                .reviewedByEmail(row.getReviewedBy() != null ? row.getReviewedBy().getEmail() : null)
                .reviewedAt(row.getReviewedAt())
                .createdUserId(row.getCreatedUser() != null ? row.getCreatedUser().getId() : null)
                .createdAt(row.getCreatedAt())
                .build();
    }

    private static String trim(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String abbreviate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
