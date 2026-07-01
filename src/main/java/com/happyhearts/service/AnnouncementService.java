package com.happyhearts.service;

import com.happyhearts.dto.PageData;
import com.happyhearts.dto.request.CreateAnnouncementRequest;
import com.happyhearts.dto.response.AnnouncementResponse;
import com.happyhearts.dto.response.RecipientCountResponse;
import com.happyhearts.enums.AnnouncementAudienceType;
import com.happyhearts.enums.AnnouncementPriority;
import com.happyhearts.enums.AnnouncementStatus;
import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.enums.Role;
import com.happyhearts.exception.AccessDeniedException;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.model.Announcement;
import com.happyhearts.model.AnnouncementRead;
import com.happyhearts.model.AnnouncementReadKey;
import com.happyhearts.model.Employee;
import com.happyhearts.model.User;
import com.happyhearts.repository.AnnouncementReadRepository;
import com.happyhearts.repository.AnnouncementRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementService {

    private static final int FOR_ME_SCAN_SIZE = 500;
    private static final int FOR_ME_MAX_MATCH = 200;

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository announcementReadRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final BranchAccessService branchAccessService;
    private final EmailService emailService;
    private final InAppNotificationService inAppNotificationService;

    @Scheduled(fixedDelay = 60_000L)
    @Transactional
    public void dispatchScheduledAnnouncements() {
        Instant now = Instant.now();
        List<Announcement> due = announcementRepository.findByStatusAndScheduledAtLessThanEqual(
                AnnouncementStatus.SCHEDULED, now);
        for (Announcement a : due) {
            try {
                List<User> recipients = resolveRecipients(a);
                a.setRecipientCount(recipients.size());
                a.setStatus(AnnouncementStatus.SENT);
                announcementRepository.save(a);
                deliverToRecipients(a, recipients, a.isEmailNotification());
            } catch (Exception ex) {
                log.error("Failed to dispatch scheduled announcement {}", a.getId(), ex);
            }
        }
    }

    @Transactional(readOnly = true)
    public PageData<AnnouncementResponse> listAdmin(
            UserPrincipal principal,
            int page,
            int size,
            String search,
            String statusFilter,
            String priorityFilter,
            String audienceFilter,
            Instant from,
            Instant to,
            Boolean myOnly
    ) {
        branchAccessService.assertAnnouncementAdmin(principal);
        boolean branchManager = principal.getRole() == Role.LEAD_TEACHER
                || principal.getRole() == Role.CENTRAL_COORDINATOR;
        Boolean effectiveMyOnly = branchManager ? Boolean.TRUE : myOnly;
        Specification<Announcement> spec = adminSpecification(
                search, statusFilter, priorityFilter, audienceFilter, from, to, effectiveMyOnly, principal.getId());
        Page<Announcement> p = announcementRepository.findAll(
                spec,
                PageRequest.of(page, Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageData.from(p.map(a -> toResponse(a, null)));
    }

    @Transactional(readOnly = true)
    public PageData<AnnouncementResponse> listForMe(
            UserPrincipal principal,
            int page,
            int size,
            String search,
            String quickFilter
    ) {
        User user = userRepository.findWithBranchById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
        Instant now = Instant.now();
        List<Announcement> candidates = announcementRepository.findAll(
                visibleToRecipientsSpec(now),
                PageRequest.of(0, FOR_ME_SCAN_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        String q = search != null ? search.trim().toLowerCase(Locale.ROOT) : "";
        String qf = quickFilter == null ? "" : quickFilter.trim();
        List<Announcement> matched = candidates.stream()
                .filter(a -> userMatchesAnnouncement(user, a))
                .filter(a -> q.isEmpty()
                        || a.getTitle().toLowerCase(Locale.ROOT).contains(q)
                        || a.getBody().toLowerCase(Locale.ROOT).contains(q))
                .filter(a -> {
                    if (qf.isEmpty() || "ALL".equalsIgnoreCase(qf)) {
                        return true;
                    }
                    boolean unread = !announcementReadRepository.existsById_AnnouncementIdAndId_UserId(a.getId(), user.getId());
                    if ("UNREAD".equalsIgnoreCase(qf)) {
                        return unread;
                    }
                    if ("IMPORTANT".equalsIgnoreCase(qf)) {
                        return a.getPriority() == AnnouncementPriority.IMPORTANT
                                || a.getPriority() == AnnouncementPriority.URGENT;
                    }
                    if ("URGENT".equalsIgnoreCase(qf)) {
                        return a.getPriority() == AnnouncementPriority.URGENT;
                    }
                    return true;
                })
                .limit(FOR_ME_MAX_MATCH)
                .toList();

        int total = matched.size();
        int fromIdx = Math.min(page * size, total);
        int toIdx = Math.min(fromIdx + size, total);
        List<Announcement> slice = matched.subList(fromIdx, toIdx);

        Set<UUID> readIds = slice.isEmpty()
                ? Set.of()
                : announcementReadRepository.findReadIds(principal.getId(), slice.stream().map(Announcement::getId).toList());

        List<AnnouncementResponse> content = slice.stream()
                .map(a -> toResponse(a, readIds.contains(a.getId())))
                .toList();

        int totalPages = Math.max(1, (int) Math.ceil(total / (double) Math.max(size, 1)));
        return PageData.<AnnouncementResponse>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }

    @Transactional(readOnly = true)
    public long countUnreadForMe(UserPrincipal principal) {
        User user = userRepository.findWithBranchById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
        Instant now = Instant.now();
        List<Announcement> candidates = announcementRepository.findAll(
                visibleToRecipientsSpec(now),
                PageRequest.of(0, FOR_ME_SCAN_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();
        return candidates.stream()
                .filter(a -> userMatchesAnnouncement(user, a))
                .filter(a -> !announcementReadRepository.existsById_AnnouncementIdAndId_UserId(a.getId(), user.getId()))
                .count();
    }

    @Transactional
    public void markRead(UserPrincipal principal, UUID announcementId) {
        Announcement a = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("error.announcement.not.found"));
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
        if (!userMatchesAnnouncement(user, a) || !isRecipientVisible(a, Instant.now())) {
            throw new BusinessException("error.announcement.not.found");
        }
        if (announcementReadRepository.existsById_AnnouncementIdAndId_UserId(announcementId, user.getId())) {
            return;
        }
        AnnouncementReadKey key = new AnnouncementReadKey(announcementId, user.getId());
        announcementReadRepository.save(AnnouncementRead.builder()
                .id(key)
                .announcement(a)
                .user(user)
                .readAt(Instant.now())
                .build());
    }

    @Transactional(readOnly = true)
    public RecipientCountResponse previewRecipientCount(UserPrincipal principal, CreateAnnouncementRequest request) {
        branchAccessService.assertAnnouncementAdmin(principal);
        validateBranchManagerAnnouncementRequest(principal, request);
        Announcement phantom = buildFromRequest(request, principal);
        return new RecipientCountResponse(resolveRecipients(phantom).size());
    }

    @Transactional
    public AnnouncementResponse publish(UserPrincipal principal, CreateAnnouncementRequest request) {
        branchAccessService.assertAnnouncementAdmin(principal);
        validateBranchManagerAnnouncementRequest(principal, request);
        User creator = userRepository.findWithBranchById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));

        Announcement entity = buildFromRequest(request, principal);
        entity.setCreatedByUser(creator);

        if (request.isSaveAsDraft()) {
            entity.setStatus(AnnouncementStatus.DRAFT);
            Announcement saved = announcementRepository.save(entity);
            return toResponse(saved, null);
        }

        if (!request.isSendImmediately()) {
            entity.setStatus(AnnouncementStatus.SCHEDULED);
            Announcement saved = announcementRepository.save(entity);
            return toResponse(saved, null);
        }

        entity.setStatus(AnnouncementStatus.SENT);
        Announcement saved = announcementRepository.save(entity);
        List<User> recipients = resolveRecipients(saved);
        saved.setRecipientCount(recipients.size());
        saved = announcementRepository.save(saved);
        deliverToRecipients(saved, recipients, request.isEmailNotification());
        return toResponse(saved, null);
    }

    @Transactional
    public AnnouncementResponse update(UserPrincipal principal, UUID id, CreateAnnouncementRequest request) {
        branchAccessService.assertAnnouncementAdmin(principal);
        Announcement existing = announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.announcement.not.found"));
        assertBranchManagerOwnsAnnouncement(principal, existing);
        validateBranchManagerAnnouncementRequest(principal, request);
        AnnouncementStatus previous = existing.getStatus();
        applyEditableFields(existing, request);

        if (request.isSaveAsDraft()) {
            existing.setStatus(AnnouncementStatus.DRAFT);
            return toResponse(announcementRepository.save(existing), null);
        }

        if (!request.isSendImmediately()) {
            existing.setStatus(AnnouncementStatus.SCHEDULED);
            return toResponse(announcementRepository.save(existing), null);
        }

        if (previous == AnnouncementStatus.SENT) {
            return toResponse(announcementRepository.save(existing), null);
        }

        existing.setStatus(AnnouncementStatus.SENT);
        Announcement saved = announcementRepository.save(existing);
        List<User> recipients = resolveRecipients(saved);
        saved.setRecipientCount(recipients.size());
        saved = announcementRepository.save(saved);
        deliverToRecipients(saved, recipients, request.isEmailNotification());
        return toResponse(saved, null);
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID id) {
        branchAccessService.assertAnnouncementAdmin(principal);
        Announcement existing = announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.announcement.not.found"));
        assertBranchManagerOwnsAnnouncement(principal, existing);
        announcementRepository.deleteById(id);
    }

    @Transactional
    public AnnouncementResponse resend(UserPrincipal principal, UUID id) {
        branchAccessService.assertAnnouncementAdmin(principal);
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.announcement.not.found"));
        if (a.getStatus() != AnnouncementStatus.SENT) {
            throw new BusinessException("error.announcement.resend.not.sent");
        }
        List<User> recipients = resolveRecipients(a);
        deliverToRecipients(a, recipients, a.isEmailNotification());
        return toResponse(announcementRepository.findById(id).orElse(a), null);
    }

    private void deliverToRecipients(Announcement a, List<User> recipients, boolean sendEmail) {
        String excerpt = excerpt(a.getBody());
        boolean anyEmailOk = false;
        if (sendEmail) {
            for (User u : recipients) {
                try {
                    emailService.sendAnnouncementEmail(u, a.getEmailSubject(), a.getTitle(), a.getBody());
                    anyEmailOk = true;
                } catch (Exception ex) {
                    log.warn("Announcement email failed for {}: {}", u.getEmail(), ex.getMessage());
                }
            }
        }
        a.setEmailSent(sendEmail && anyEmailOk);
        announcementRepository.save(a);

        for (User u : recipients) {
            try {
                inAppNotificationService.createForUser(u.getId(), a.getTitle(), excerpt, "ANNOUNCEMENT");
            } catch (Exception ex) {
                log.warn("Announcement in-app failed for {}: {}", u.getId(), ex.getMessage());
            }
        }
    }

    private void applyEditableFields(Announcement existing, CreateAnnouncementRequest request) {
        existing.setTitle(request.getTitle().trim());
        existing.setBody(request.getBody().trim());
        existing.setPriority(request.getPriority() != null ? request.getPriority() : AnnouncementPriority.NORMAL);
        existing.setAudienceType(request.getAudienceType());
        existing.setSendToAll(request.getAudienceType() == AnnouncementAudienceType.EVERYONE);
        existing.setTargetRoles(rolesToCsv(request.getRoles()));
        existing.setTargetBranchIds(branchIdsToCsv(request.getBranchIds()));
        existing.setTargetCategories(categoriesToCsv(request.getCategories()));
        existing.setSendImmediately(request.isSendImmediately());
        existing.setScheduledAt(request.isSendImmediately() ? null : request.getScheduledAt());
        existing.setExpiresAt(request.getExpiresAt());
        existing.setEmailNotification(request.isEmailNotification());
        existing.setEmailSubject(StringUtils.hasText(request.getEmailSubject()) ? request.getEmailSubject().trim() : null);
    }

    private void validateBranchManagerAnnouncementRequest(UserPrincipal principal, CreateAnnouncementRequest request) {
        if (principal.getRole() != Role.LEAD_TEACHER && principal.getRole() != Role.CENTRAL_COORDINATOR) {
            return;
        }
        if (request.getAudienceType() == AnnouncementAudienceType.EVERYONE
                || request.getAudienceType() == AnnouncementAudienceType.ROLE
                || request.getAudienceType() == AnnouncementAudienceType.CUSTOM) {
            throw new AccessDeniedException();
        }
        if (request.getAudienceType() == AnnouncementAudienceType.BRANCH) {
            List<UUID> branchIds = request.getBranchIds() == null ? List.of() : request.getBranchIds();
            if (branchIds.size() != 1 || principal.getBranchId() == null
                    || !branchIds.get(0).equals(principal.getBranchId())) {
                throw new AccessDeniedException();
            }
        }
    }

    private void assertBranchManagerOwnsAnnouncement(UserPrincipal principal, Announcement announcement) {
        if (principal.getRole() != Role.LEAD_TEACHER && principal.getRole() != Role.CENTRAL_COORDINATOR) {
            return;
        }
        if (announcement.getCreatedByUser() == null
                || !announcement.getCreatedByUser().getId().equals(principal.getId())) {
            throw new AccessDeniedException();
        }
    }

    private Announcement buildFromRequest(CreateAnnouncementRequest request, UserPrincipal principal) {
        return Announcement.builder()
                .title(request.getTitle().trim())
                .body(request.getBody().trim())
                .priority(request.getPriority() != null ? request.getPriority() : AnnouncementPriority.NORMAL)
                .audienceType(request.getAudienceType())
                .sendToAll(request.getAudienceType() == AnnouncementAudienceType.EVERYONE)
                .targetRoles(rolesToCsv(request.getRoles()))
                .targetBranchIds(branchIdsToCsv(request.getBranchIds()))
                .targetCategories(categoriesToCsv(request.getCategories()))
                .sendImmediately(request.isSendImmediately())
                .scheduledAt(request.isSendImmediately() ? null : request.getScheduledAt())
                .expiresAt(request.getExpiresAt())
                .emailNotification(request.isEmailNotification())
                .emailSubject(StringUtils.hasText(request.getEmailSubject()) ? request.getEmailSubject().trim() : null)
                .createdByEmail(principal.getEmail())
                .build();
    }

    private List<User> resolveRecipients(Announcement a) {
        return switch (a.getAudienceType()) {
            case EVERYONE -> userRepository.findAllActiveWithBranch();
            case ROLE -> {
                Set<Role> roles = parseRolesCsv(a.getTargetRoles());
                if (roles.isEmpty()) {
                    yield List.of();
                }
                yield userRepository.findAllActiveWithBranchByRoleIn(roles);
            }
            case BRANCH -> {
                Set<UUID> ids = parseUuidCsv(a.getTargetBranchIds());
                if (ids.isEmpty()) {
                    yield List.of();
                }
                yield userRepository.findAllActiveWithBranchByBranchIdIn(ids);
            }
            case CATEGORY -> {
                Set<EmployeeCategory> cats = parseCategoriesCsv(a.getTargetCategories());
                yield usersFromCategories(cats);
            }
            case CUSTOM -> resolveCustomRecipients(
                    parseRolesCsv(a.getTargetRoles()),
                    parseUuidCsv(a.getTargetBranchIds()),
                    parseCategoriesCsv(a.getTargetCategories())
            );
        };
    }

    private List<User> usersFromCategories(Set<EmployeeCategory> cats) {
        if (cats.isEmpty()) {
            return List.of();
        }
        return employeeRepository.findActiveWithUserByCategoryIn(cats).stream()
                .map(Employee::getUser)
                .filter(Objects::nonNull)
                .filter(User::isActive)
                .distinct()
                .toList();
    }

    private List<User> resolveCustomRecipients(Set<Role> roles, Set<UUID> branchIds, Set<EmployeeCategory> categories) {
        if (roles.isEmpty() && branchIds.isEmpty() && categories.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllActiveWithBranch().stream()
                .filter(u -> matchesCustom(u, roles, branchIds, categories))
                .distinct()
                .toList();
    }

    private boolean matchesCustom(User u, Set<Role> roles, Set<UUID> branchIds, Set<EmployeeCategory> categories) {
        boolean roleOk = roles.isEmpty() || roles.contains(u.getRole());
        boolean branchOk = branchIds.isEmpty()
                || (u.getBranch() != null && branchIds.contains(u.getBranch().getId()));
        boolean catOk = categories.isEmpty() || employeeMatchesCategories(u, categories);
        return roleOk && branchOk && catOk;
    }

    private boolean employeeMatchesCategories(User u, Set<EmployeeCategory> categories) {
        List<Employee> emps = employeeRepository.findAllByUser_Id(u.getId());
        return emps.stream().anyMatch(e -> e.isEmploymentActive() && categories.contains(e.getCategory()));
    }

    private boolean userMatchesAnnouncement(User u, Announcement a) {
        if (!u.isActive()) {
            return false;
        }
        return switch (a.getAudienceType()) {
            case EVERYONE -> true;
            case ROLE -> {
                Set<Role> roles = parseRolesCsv(a.getTargetRoles());
                yield !roles.isEmpty() && roles.contains(u.getRole());
            }
            case BRANCH -> {
                Set<UUID> ids = parseUuidCsv(a.getTargetBranchIds());
                yield u.getBranch() != null && ids.contains(u.getBranch().getId());
            }
            case CATEGORY -> {
                Set<EmployeeCategory> cats = parseCategoriesCsv(a.getTargetCategories());
                yield employeeMatchesCategories(u, cats);
            }
            case CUSTOM -> {
                Set<Role> roles = parseRolesCsv(a.getTargetRoles());
                Set<UUID> branchIds = parseUuidCsv(a.getTargetBranchIds());
                Set<EmployeeCategory> categories = parseCategoriesCsv(a.getTargetCategories());
                if (roles.isEmpty() && branchIds.isEmpty() && categories.isEmpty()) {
                    yield false;
                }
                yield matchesCustom(u, roles, branchIds, categories);
            }
        };
    }

    private boolean isRecipientVisible(Announcement a, Instant now) {
        if (a.getStatus() != AnnouncementStatus.SENT) {
            return false;
        }
        return a.getExpiresAt() == null || a.getExpiresAt().isAfter(now);
    }

    private Specification<Announcement> visibleToRecipientsSpec(Instant now) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), AnnouncementStatus.SENT),
                cb.or(cb.isNull(root.get("expiresAt")), cb.greaterThan(root.get("expiresAt"), now))
        );
    }

    private Specification<Announcement> adminSpecification(
            String search,
            String statusFilter,
            String priorityFilter,
            String audienceFilter,
            Instant from,
            Instant to,
            Boolean myOnly,
            UUID myUserId
    ) {
        return (root, query, cb) -> {
            List<Predicate> parts = new ArrayList<>();
            Instant now = Instant.now();

            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                parts.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("body")), pattern)
                ));
            }

            if (Boolean.TRUE.equals(myOnly) && myUserId != null) {
                parts.add(cb.equal(root.get("createdByUser").get("id"), myUserId));
            }

            if (from != null) {
                parts.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                parts.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            if (StringUtils.hasText(priorityFilter) && !"ALL".equalsIgnoreCase(priorityFilter)) {
                parts.add(cb.equal(root.get("priority"), AnnouncementPriority.valueOf(priorityFilter.toUpperCase(Locale.ROOT))));
            }

            if (StringUtils.hasText(audienceFilter) && !"ALL".equalsIgnoreCase(audienceFilter)) {
                parts.add(cb.equal(root.get("audienceType"),
                        AnnouncementAudienceType.valueOf(audienceFilter.toUpperCase(Locale.ROOT))));
            }

            if (StringUtils.hasText(statusFilter) && !"ALL".equalsIgnoreCase(statusFilter)) {
                String s = statusFilter.toUpperCase(Locale.ROOT);
                if ("EXPIRED".equals(s)) {
                    parts.add(cb.and(
                            cb.isNotNull(root.get("expiresAt")),
                            cb.lessThan(root.get("expiresAt"), now)
                    ));
                } else {
                    parts.add(cb.equal(root.get("status"), AnnouncementStatus.valueOf(s)));
                    if ("SENT".equals(s)) {
                        parts.add(cb.or(
                                cb.isNull(root.get("expiresAt")),
                                cb.greaterThan(root.get("expiresAt"), now)
                        ));
                    }
                }
            }

            return cb.and(parts.toArray(new Predicate[0]));
        };
    }

    private String rolesToCsv(List<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        return roles.stream().distinct().map(Role::name).collect(Collectors.joining(","));
    }

    private String branchIdsToCsv(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().distinct().map(UUID::toString).collect(Collectors.joining(","));
    }

    private String categoriesToCsv(List<EmployeeCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        return categories.stream().distinct().map(EmployeeCategory::name).collect(Collectors.joining(","));
    }

    private Set<Role> parseRolesCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Set.of();
        }
        Set<Role> out = new LinkedHashSet<>();
        for (String p : csv.split(",")) {
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(Role.valueOf(t));
            } catch (IllegalArgumentException ignored) {
                // skip invalid token
            }
        }
        return out;
    }

    private Set<UUID> parseUuidCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Set.of();
        }
        Set<UUID> out = new LinkedHashSet<>();
        for (String p : csv.split(",")) {
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(UUID.fromString(t));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
        return out;
    }

    private Set<EmployeeCategory> parseCategoriesCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Set.of();
        }
        Set<EmployeeCategory> out = new LinkedHashSet<>();
        for (String p : csv.split(",")) {
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(EmployeeCategory.valueOf(t));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
        return out;
    }

    private String excerpt(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 280 ? body.substring(0, 277) + "…" : body;
    }

    private AnnouncementResponse toResponse(Announcement a, Boolean readOverride) {
        List<UUID> branchIds = new ArrayList<>(parseUuidCsv(a.getTargetBranchIds()));
        List<String> catList = new ArrayList<>(parseCategoriesCsv(a.getTargetCategories()).stream().map(Enum::name).toList());
        Instant now = Instant.now();
        String display = computeDisplayStatus(a, now);
        return AnnouncementResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .body(a.getBody())
                .sendToAll(a.isSendToAll())
                .audienceType(a.getAudienceType())
                .targetRoles(a.getTargetRoles())
                .targetBranchIds(branchIds.isEmpty() ? List.of() : branchIds)
                .targetCategories(catList)
                .priority(a.getPriority())
                .createdByEmail(a.getCreatedByEmail())
                .createdByUserId(a.getCreatedByUser() != null ? a.getCreatedByUser().getId() : null)
                .createdAt(a.getCreatedAt())
                .scheduledAt(a.getScheduledAt())
                .expiresAt(a.getExpiresAt())
                .sendImmediately(a.isSendImmediately())
                .emailNotification(a.isEmailNotification())
                .emailSubject(a.getEmailSubject())
                .status(a.getStatus())
                .displayStatus(display)
                .emailSent(a.isEmailSent())
                .recipientCount(a.getRecipientCount())
                .read(readOverride)
                .build();
    }

    private String computeDisplayStatus(Announcement a, Instant now) {
        if (a.getExpiresAt() != null && a.getExpiresAt().isBefore(now)) {
            return "EXPIRED";
        }
        return a.getStatus().name();
    }
}
