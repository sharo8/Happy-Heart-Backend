package com.happyhearts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happyhearts.config.AuthOtpProperties;
import com.happyhearts.dto.PageData;
import com.happyhearts.dto.request.CreateFeedbackRequest;
import com.happyhearts.dto.request.UpdateFeedbackRequest;
import com.happyhearts.dto.response.FeedbackRecipientOptionResponse;
import com.happyhearts.dto.response.FeedbackResponse;
import com.happyhearts.enums.FeedbackType;
import com.happyhearts.enums.FeedbackVisibility;
import com.happyhearts.enums.NotificationStatus;
import com.happyhearts.enums.NotificationType;
import com.happyhearts.enums.Role;
import com.happyhearts.exception.AccessDeniedException;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Feedback;
import com.happyhearts.model.User;
import com.happyhearts.repository.FeedbackRepository;
import com.happyhearts.repository.FeedbackSpecs;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.util.EmailHtmlTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AuthOtpProperties authOtpProperties;
    private final GmPermissionService gmPermissionService;

    @Transactional(readOnly = true)
    public PageData<FeedbackResponse> list(UserPrincipal principal, int page, int size, String q, String sort, String dir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = switch (sort != null ? sort : "") {
            case "type" -> "type";
            case "visibility" -> "visibility";
            default -> "createdAt";
        };
        PageRequest pr = PageRequest.of(page, size, Sort.by(direction, sortField));
        Specification<Feedback> spec = FeedbackSpecs.forPrincipal(principal);
        if (StringUtils.hasText(q)) {
            spec = spec.and(FeedbackSpecs.search(q));
        }
        Page<Feedback> pg = feedbackRepository.findAll(spec, pr);
        return PageData.from(pg.map(this::map));
    }

    @Transactional(readOnly = true)
    public FeedbackResponse getById(UserPrincipal principal, UUID id) {
        Feedback f = feedbackRepository.findById(id).orElseThrow(() -> new BusinessException("error.feedback.not.found"));
        assertCanView(principal, f);
        return map(f);
    }

    /**
     * Portal users the actor may address with a feedback (scoped by role).
     */
    @Transactional(readOnly = true)
    public List<FeedbackRecipientOptionResponse> recipientSuggestions(UserPrincipal principal) {
        List<User> candidates = switch (principal.getRole()) {
            case SUPER_ADMIN -> userRepository.findAllActiveWithBranch();
            case GENERAL_MANAGER_PEDAGOGIQUE -> {
                List<User> mix = new ArrayList<>();
                mix.addAll(userRepository.findAllActiveWithBranchByRoleIn(List.of(Role.SUPER_ADMIN)));
                mix.addAll(userRepository.findAllActiveWithBranchByRoleIn(List.of(Role.CENTRAL_COORDINATOR)));
                yield mix;
            }
            case CENTRAL_COORDINATOR, LEAD_TEACHER -> {
                UUID bid = principal.getBranchId();
                if (bid == null) {
                    yield List.of();
                }
                List<User> scoped = new ArrayList<>(userRepository.findAllActiveWithBranchByBranchId(bid));
                scoped.removeIf(u -> u.getRole() == Role.SUPER_ADMIN || u.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE);
                yield scoped;
            }
            default -> List.of();
        };
        Map<UUID, FeedbackRecipientOptionResponse> byId = new LinkedHashMap<>();
        for (User u : candidates) {
            if (u.getId().equals(principal.getId())) {
                continue;
            }
            byId.put(u.getId(), toRecipientOption(u));
        }
        return byId.values().stream()
                .sorted(Comparator.comparing(FeedbackRecipientOptionResponse::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private FeedbackRecipientOptionResponse toRecipientOption(User u) {
        return FeedbackRecipientOptionResponse.builder()
                .userId(u.getId())
                .email(u.getEmail())
                .displayName(displayName(u))
                .role(u.getRole())
                .branchCode(u.getBranch() != null ? u.getBranch().getCode() : null)
                .roleLabel(u.getRole() != null ? u.getRole().name().replace('_', ' ') : null)
                .build();
    }

    @Transactional
    public FeedbackResponse create(UserPrincipal principal, CreateFeedbackRequest req) {
        if (principal.getRole().isStaffSelfService()) {
            throw new AccessDeniedException();
        }
        User fromUser = userRepository.findWithBranchById(principal.getId()).orElseThrow(() -> new BusinessException("error.user.not.found"));
        User toUser = userRepository.findWithBranchById(req.getToUserId()).orElseThrow(() -> new BusinessException("error.user.not.found"));
        if (fromUser.getId().equals(toUser.getId())) {
            throw new BusinessException("error.feedback.self");
        }
        validateCreate(fromUser, toUser, req.getType(), req.getVisibility());

        UUID branchId = resolveBranchId(toUser, fromUser);
        Branch branchRef = branchId != null ? Branch.builder().id(branchId).build() : null;

        boolean sendMail = req.getSentByEmail() == null || Boolean.TRUE.equals(req.getSentByEmail());

        Feedback row = Feedback.builder()
                .fromUser(fromUser)
                .toUser(toUser)
                .type(req.getType())
                .content(req.getContent().trim())
                .visibility(req.getVisibility())
                .sentByEmail(sendMail)
                .branch(branchRef)
                .build();
        row = feedbackRepository.save(row);

        Instant emailAt = null;
        if (sendMail) {
            List<String> cc = buildSupervisorCcEmails(req.getVisibility());
            String portal = authOtpProperties.getFrontendBaseUrl().replaceAll("/$", "");
            String senderLabel = displayName(fromUser);
            String subject = "Nouveau feedback de " + senderLabel + " — Happy Hearts";
            String plain = """
                    Type: %s
                    Visibilité: %s
                    De: %s
                    Message:
                    %s
                    """.formatted(req.getType(), req.getVisibility(), senderLabel, req.getContent());
            String html = EmailHtmlTemplates.portalFeedbackHtml(
                    toUser.getPreferredLanguage(),
                    senderLabel,
                    req.getType().name(),
                    req.getVisibility().name(),
                    req.getContent(),
                    portal,
                    emailService.getBrandLogoUrl()
            );
            NotificationStatus st = emailService.sendPortalNotificationEmail(
                    toUser.getEmail(),
                    cc,
                    subject,
                    plain,
                    html,
                    toUser.getPreferredLanguage(),
                    NotificationType.FEEDBACK
            );
            if (st == NotificationStatus.SENT) {
                emailAt = Instant.now();
                row.setEmailSentAt(emailAt);
                feedbackRepository.save(row);
            }
        }

        try {
            auditService.log(
                    principal,
                    "FEEDBACK_SENT",
                    row.getId(),
                    "feedback",
                    objectMapper.writeValueAsString(Map.of(
                            "toUserId", toUser.getId().toString(),
                            "type", req.getType().name(),
                            "visibility", req.getVisibility().name()
                    )),
                    branchId
            );
        } catch (Exception ex) {
            log.warn("audit FEEDBACK_SENT failed: {}", ex.getMessage());
        }

        return map(row);
    }

    @Transactional
    public FeedbackResponse update(UserPrincipal principal, UUID id, UpdateFeedbackRequest req) {
        Feedback row = feedbackRepository.findById(id).orElseThrow(() -> new BusinessException("error.feedback.not.found"));
        assertCanView(principal, row);

        boolean isOwn = row.getFromUser() != null && row.getFromUser().getId().equals(principal.getId());
        boolean isSuperAdmin = principal.getRole() == Role.SUPER_ADMIN;

        if (principal.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            if (!isOwn && !gmPermissionService.isAllowed(principal.getId(), "evaluations", "update")) {
                throw new AccessDeniedException();
            }
        } else if (!FeedbackSpecs.canMutate(principal, row)) {
            throw new AccessDeniedException();
        }

        if (isSuperAdmin && !isOwn) {
            if (req.getEditReason() == null || req.getEditReason().isBlank()) {
                throw new BusinessException("error.feedback.edit.reason.required");
            }
            row.setEditReason(req.getEditReason().trim());
        }

        if (req.getType() != null) {
            row.setType(req.getType());
        }
        if (req.getVisibility() != null) {
            row.setVisibility(req.getVisibility());
        }
        row.setContent(req.getContent().trim());
        User editor = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException("error.user.not.found"));
        row.setUpdatedByUser(editor);
        row = feedbackRepository.save(row);
        auditFeedback(principal, "FEEDBACK_UPDATED", row);
        return map(row);
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID id) {
        Feedback row = feedbackRepository.findById(id).orElseThrow(() -> new BusinessException("error.feedback.not.found"));
        assertCanView(principal, row);

        boolean isOwn = row.getFromUser() != null && row.getFromUser().getId().equals(principal.getId());
        if (principal.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            if (!isOwn && !gmPermissionService.isAllowed(principal.getId(), "evaluations", "delete")) {
                throw new AccessDeniedException();
            }
        } else if (!FeedbackSpecs.canMutate(principal, row)) {
            throw new AccessDeniedException();
        }

        auditFeedback(principal, "FEEDBACK_DELETED", row);
        feedbackRepository.delete(row);
    }

    private void auditFeedback(UserPrincipal principal, String action, Feedback row) {
        try {
            auditService.log(
                    principal,
                    action,
                    row.getId(),
                    "feedback",
                    objectMapper.writeValueAsString(Map.of(
                            "toUserId", row.getToUser().getId().toString(),
                            "type", row.getType().name(),
                            "visibility", row.getVisibility().name()
                    )),
                    row.getBranch() != null ? row.getBranch().getId() : null
            );
        } catch (Exception ex) {
            log.warn("audit {} failed: {}", action, ex.getMessage());
        }
    }

    private List<String> buildSupervisorCcEmails(FeedbackVisibility visibility) {
        if (visibility != FeedbackVisibility.SUPERIORS) {
            return List.of();
        }
        List<User> ccUsers = userRepository.findAllActiveWithBranchByRoleIn(List.of(Role.SUPER_ADMIN, Role.GENERAL_MANAGER_PEDAGOGIQUE));
        List<String> emails = new ArrayList<>();
        for (User u : ccUsers) {
            if (StringUtils.hasText(u.getEmail())) {
                emails.add(u.getEmail().trim());
            }
        }
        return emails;
    }

    private void validateCreate(User from, User to, FeedbackType type, FeedbackVisibility visibility) {
        Role fr = from.getRole();
        Role tr = to.getRole();

        if (fr == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            if (tr == Role.SUPER_ADMIN) {
                if (type != FeedbackType.RAPPORT_GMP || visibility != FeedbackVisibility.PRIVATE) {
                    throw new BusinessException("error.feedback.gmp.superadmin.rule");
                }
                return;
            }
            if (tr == Role.CENTRAL_COORDINATOR) {
                if (type == FeedbackType.RAPPORT_GMP) {
                    throw new BusinessException("error.feedback.gmp.invalid.type");
                }
                return;
            }
            throw new BusinessException("error.feedback.gmp.invalid.target");
        }

        if (fr == Role.CENTRAL_COORDINATOR || fr == Role.LEAD_TEACHER) {
            if (from.getBranch() == null || to.getBranch() == null
                    || !from.getBranch().getId().equals(to.getBranch().getId())) {
                throw new BusinessException("error.feedback.branch.mismatch");
            }
            if (type == FeedbackType.RAPPORT_GMP) {
                throw new BusinessException("error.feedback.type.not.allowed");
            }
            return;
        }

        if (fr == Role.SUPER_ADMIN) {
            return;
        }

        throw new AccessDeniedException();
    }

    private UUID resolveBranchId(User toUser, User fromUser) {
        if (toUser.getBranch() != null) {
            return toUser.getBranch().getId();
        }
        if (fromUser.getBranch() != null) {
            return fromUser.getBranch().getId();
        }
        return null;
    }

    private void assertCanView(UserPrincipal principal, Feedback f) {
        switch (principal.getRole()) {
            case SUPER_ADMIN -> { }
            case GENERAL_MANAGER_PEDAGOGIQUE -> {
                boolean ok = f.getToUser().getId().equals(principal.getId())
                        || f.getVisibility() == FeedbackVisibility.SUPERIORS
                        || f.getVisibility() == FeedbackVisibility.PUBLIC;
                if (!ok) {
                    throw new AccessDeniedException();
                }
            }
            case CENTRAL_COORDINATOR, LEAD_TEACHER -> {
                UUID bid = principal.getBranchId();
                boolean sameBranch = f.getBranch() != null && bid != null && f.getBranch().getId().equals(bid);
                boolean involved = f.getToUser().getId().equals(principal.getId()) || f.getFromUser().getId().equals(principal.getId());
                boolean shared = f.getVisibility() == FeedbackVisibility.SUPERIORS || f.getVisibility() == FeedbackVisibility.PUBLIC;
                if (!(involved || (sameBranch && shared))) {
                    throw new AccessDeniedException();
                }
            }
            case ASSISTANT, COOK, CLEANER, TEACHER -> {
                if (!f.getToUser().getId().equals(principal.getId())) {
                    throw new AccessDeniedException();
                }
            }
            default -> throw new AccessDeniedException();
        }
    }

    private FeedbackResponse map(Feedback f) {
        User from = f.getFromUser();
        User to = f.getToUser();
        return FeedbackResponse.builder()
                .id(f.getId())
                .fromUserId(from.getId())
                .fromEmail(from.getEmail())
                .fromDisplayName(displayName(from))
                .toUserId(to.getId())
                .toEmail(to.getEmail())
                .toDisplayName(displayName(to))
                .type(f.getType())
                .content(f.getContent())
                .visibility(f.getVisibility())
                .sentByEmail(f.isSentByEmail())
                .emailSentAt(f.getEmailSentAt())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .updatedByUserId(f.getUpdatedByUser() != null ? f.getUpdatedByUser().getId() : null)
                .updatedByDisplayName(f.getUpdatedByUser() != null ? displayName(f.getUpdatedByUser()) : null)
                .branchId(f.getBranch() != null ? f.getBranch().getId() : null)
                .branchCode(f.getBranch() != null ? f.getBranch().getCode() : null)
                .build();
    }

    private String displayName(User u) {
        String fn = u.getFirstName() != null ? u.getFirstName().trim() : "";
        String ln = u.getLastName() != null ? u.getLastName().trim() : "";
        String both = (fn + " " + ln).trim();
        if (!both.isEmpty()) {
            return both;
        }
        return u.getEmail();
    }
}
