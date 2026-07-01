package com.happyhearts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happyhearts.config.AuthOtpProperties;
import com.happyhearts.dto.PageData;
import com.happyhearts.dto.request.CreateConversationRequest;
import com.happyhearts.dto.request.PostMessageRequest;
import com.happyhearts.dto.response.ConversationSummaryResponse;
import com.happyhearts.dto.response.FeedbackRecipientOptionResponse;
import com.happyhearts.dto.response.MessageReactionResponse;
import com.happyhearts.dto.response.MessageResponse;
import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.enums.Language;
import com.happyhearts.enums.Role;
import com.happyhearts.exception.AccessDeniedException;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Conversation;
import com.happyhearts.model.ConversationParticipant;
import com.happyhearts.model.Message;
import com.happyhearts.model.User;
import com.happyhearts.model.Employee;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.ConversationParticipantRepository;
import com.happyhearts.repository.ConversationRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.MessageRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.util.RoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final AuditService auditService;
    private final InAppNotificationService inAppNotificationService;
    private final ObjectMapper objectMapper;
    private final AuthOtpProperties authOtpProperties;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public List<FeedbackRecipientOptionResponse> messagingRecipientSuggestions(UserPrincipal principal) {
        Map<UUID, FeedbackRecipientOptionResponse> byId = new LinkedHashMap<>();

        switch (principal.getRole()) {
            case SUPER_ADMIN, GENERAL_MANAGER_PEDAGOGIQUE -> addAllActivePortalUsers(byId, principal);
            case CENTRAL_COORDINATOR, LEAD_TEACHER -> {
                UUID bid = principal.getBranchId();
                if (bid != null) {
                    addBranchPortalUsers(byId, principal, bid);
                }
                for (User u : userRepository.findAllActiveWithBranchByRoleIn(
                        List.of(Role.SUPER_ADMIN, Role.GENERAL_MANAGER_PEDAGOGIQUE))) {
                    addRecipient(byId, principal, u);
                }
            }
            case ASSISTANT, COOK, CLEANER, TEACHER -> addAllActivePortalUsers(byId, principal);
        }

        return byId.values().stream()
                .sorted(Comparator.comparing(FeedbackRecipientOptionResponse::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void addAllActivePortalUsers(Map<UUID, FeedbackRecipientOptionResponse> byId, UserPrincipal principal) {
        for (Employee emp : employeeRepository.findAllActiveWithBranch()) {
            addEmployeeRecipient(byId, principal, emp, true);
        }
        for (User u : userRepository.findAllActiveWithBranch()) {
            if (!byId.containsKey(u.getId())) {
                addRecipient(byId, principal, u, null);
            }
        }
    }

    private void addBranchPortalUsers(Map<UUID, FeedbackRecipientOptionResponse> byId, UserPrincipal principal, UUID branchId) {
        for (Employee emp : employeeRepository.findAllActiveByBranch_Id(branchId)) {
            addEmployeeRecipient(byId, principal, emp, principal.getRole() == Role.SUPER_ADMIN);
        }
        for (User u : userRepository.findAllActiveWithBranchByBranchId(branchId)) {
            if (!byId.containsKey(u.getId())) {
                addRecipient(byId, principal, u, null);
            }
        }
    }

    private void addEmployeeRecipient(
            Map<UUID, FeedbackRecipientOptionResponse> byId,
            UserPrincipal principal,
            Employee emp,
            boolean autoProvision
    ) {
        if (!StringUtils.hasText(emp.getEmail())) {
            return;
        }
        try {
            User user = resolveUserForEmployee(emp, autoProvision);
            if (user == null || !user.isActive() || user.getId().equals(principal.getId())) {
                return;
            }
            addRecipient(byId, principal, user, emp);
        } catch (RuntimeException ex) {
            log.warn("Skipping messaging recipient for employee {} ({}): {}",
                    emp.getId(), emp.getEmail(), ex.getMessage());
        }
    }

    private User resolveUserForEmployee(Employee emp, boolean autoProvision) {
        Employee loaded = employeeRepository.findWithBranchAndUserById(emp.getId()).orElse(emp);
        if (loaded.getUser() != null && loaded.getUser().isActive()) {
            return userRepository.findWithBranchById(loaded.getUser().getId()).orElse(loaded.getUser());
        }
        String email = loaded.getEmail().trim();
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            User user = userRepository.findWithBranchById(existing.get().getId()).orElseThrow();
            linkEmployeeToUser(loaded, user);
            return user;
        }
        if (!autoProvision) {
            return null;
        }
        return createStaffPortalUser(loaded);
    }

    private void linkEmployeeToUser(Employee employee, User user) {
        if (isUserLinkedToAnotherEmployee(user.getId(), employee.getId())) {
            return;
        }
        Role targetRole = resolvePortalRole(employee, user);
        user.setRole(targetRole);
        if (targetRole != Role.SUPER_ADMIN && targetRole != Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            user.setBranch(employee.getBranch());
        }
        user.setFirstName(trim(employee.getFirstName()));
        user.setLastName(trim(employee.getLastName()));
        user.setPreferredLanguage(resolvePreferredLanguage(employee));
        user.setActive(true);
        userRepository.save(user);
        employee.setUser(user);
        employeeRepository.save(employee);
    }

    private boolean isUserLinkedToAnotherEmployee(UUID userId, UUID employeeId) {
        return employeeRepository.findAllByUser_Id(userId).stream()
                .anyMatch(e -> !e.getId().equals(employeeId));
    }

    private Role resolvePortalRole(Employee employee, User user) {
        if (user.getRole() == Role.SUPER_ADMIN || user.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            return user.getRole();
        }
        return resolvePortalRoleForEmployee(employee);
    }

    private Role resolvePortalRoleForEmployee(Employee employee) {
        if (employee.getCategory() == EmployeeCategory.MANAGEMENT) {
            return Role.SUPER_ADMIN;
        }
        return RoleMapper.fromEmployeeCategory(employee.getCategory());
    }

    private static Language resolvePreferredLanguage(Employee employee) {
        return employee.getPreferredLanguage() != null ? employee.getPreferredLanguage() : Language.FR;
    }

    private User createStaffPortalUser(Employee employee) {
        byte[] randomPwd = new byte[24];
        new SecureRandom().nextBytes(randomPwd);
        String internalPassword = passwordEncoder.encode(Base64.getEncoder().encodeToString(randomPwd));
        String setupToken = generateSetupToken();
        Instant setupExpiry = Instant.now().plus(authOtpProperties.getSetupTokenExpirationHours(), ChronoUnit.HOURS);
        Role role = resolvePortalRoleForEmployee(employee);

        User user = User.builder()
                .email(employee.getEmail().trim().toLowerCase())
                .firstName(trim(employee.getFirstName()))
                .lastName(trim(employee.getLastName()))
                .password(internalPassword)
                .role(role)
                .preferredLanguage(resolvePreferredLanguage(employee))
                .branch(role == Role.SUPER_ADMIN || role == Role.GENERAL_MANAGER_PEDAGOGIQUE ? null : employee.getBranch())
                .active(true)
                .passwordChangeRequired(false)
                .initialSetupToken(setupToken)
                .initialSetupTokenExpiresAt(setupExpiry)
                .build();
        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            user = userRepository.findByEmailIgnoreCase(employee.getEmail().trim())
                    .flatMap(u -> userRepository.findWithBranchById(u.getId()))
                    .orElseThrow(() -> ex);
        }
        if (!isUserLinkedToAnotherEmployee(user.getId(), employee.getId())) {
            employee.setUser(user);
            employeeRepository.save(employee);
        }
        log.info("Auto-provisioned portal user for employee {} ({})", employee.getId(), role);
        return user;
    }

    private static String generateSetupToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String trim(String s) {
        return s != null ? s.trim() : null;
    }

    private void addRecipient(
            Map<UUID, FeedbackRecipientOptionResponse> byId,
            UserPrincipal principal,
            User u,
            Employee emp
    ) {
        if (u == null || !u.isActive() || u.getId().equals(principal.getId())) {
            return;
        }
        byId.put(u.getId(), toRecipientOption(u, emp));
    }

    private void addRecipient(Map<UUID, FeedbackRecipientOptionResponse> byId, UserPrincipal principal, User u) {
        addRecipient(byId, principal, u, findEmployeeForUser(u));
    }

    private Employee findEmployeeForUser(User u) {
        List<Employee> rows = employeeRepository.findAllByUser_Id(u.getId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<User> staffAllowedRecipients(UserPrincipal principal) {
        UUID bid = principal.getBranchId();
        if (bid == null) {
            return List.of();
        }
        List<User> out = new ArrayList<>(userRepository.findByBranch_IdAndRole(bid, Role.CENTRAL_COORDINATOR));
        branchRepository.findWithLeadersById(bid).ifPresent(b -> {
            if (b.getLeadTeacher() != null && b.getLeadTeacher().getUser() != null) {
                out.add(b.getLeadTeacher().getUser());
            }
            if (b.getSecondTeacher() != null && b.getSecondTeacher().getUser() != null) {
                out.add(b.getSecondTeacher().getUser());
            }
        });
        return out;
    }

    private FeedbackRecipientOptionResponse toRecipientOption(User u, Employee emp) {
        EmployeeCategory cat = emp != null ? emp.getCategory() : null;
        String categoryName = cat != null ? cat.name() : null;
        String label = categoryLabel(cat, u.getRole());
        String photo = u.getProfilePhotoUrl();
        if (!StringUtils.hasText(photo) && emp != null) {
            photo = emp.getProfilePhotoUrl();
        }
        String contactEmail = u.getEmail();
        if (!StringUtils.hasText(contactEmail) && emp != null && StringUtils.hasText(emp.getEmail())) {
            contactEmail = emp.getEmail().trim();
        }
        return FeedbackRecipientOptionResponse.builder()
                .userId(u.getId())
                .email(contactEmail != null ? contactEmail.trim() : "")
                .displayName(displayName(u))
                .role(u.getRole())
                .branchCode(u.getBranch() != null ? u.getBranch().getCode() : (emp != null && emp.getBranch() != null ? emp.getBranch().getCode() : null))
                .employeeCategory(categoryName)
                .roleLabel(label)
                .profilePhotoUrl(StringUtils.hasText(photo) ? photo.trim() : null)
                .build();
    }

    private static String categoryLabel(EmployeeCategory category, Role portalRole) {
        if (category != null) {
            return switch (category) {
                case COOK -> "Cuisinier";
                case SECURITY_GUARD -> "Gardien";
                case TEACHER_ASSISTANT -> "Assistant(e)";
                case CLEANER -> "Agent d'entretien";
                case LEAD_TEACHER -> "Enseignant principal";
                case COMMUNICATION_STAFF -> "Communication";
                case LOGISTICS -> "Logistique";
                case MANAGEMENT -> "Direction";
            };
        }
        if (portalRole == null) {
            return "";
        }
        return switch (portalRole) {
            case SUPER_ADMIN -> "Super Admin";
            case GENERAL_MANAGER_PEDAGOGIQUE -> "Directeur pédagogique";
            case CENTRAL_COORDINATOR -> "Coordinateur central";
            case LEAD_TEACHER -> "Enseignant principal";
            case ASSISTANT -> "Assistant(e)";
            case COOK -> "Cuisinier";
            case CLEANER -> "Agent d'entretien";
            case TEACHER -> "Enseignant";
        };
    }

    @Transactional(readOnly = true)
    public PageData<ConversationSummaryResponse> listConversations(UserPrincipal principal, int page, int size) {
        PageRequest pr = PageRequest.of(page, size);
        Page<Conversation> pg = conversationRepository.findForParticipant(principal.getId(), pr);
        List<UUID> ids = pg.getContent().stream().map(Conversation::getId).toList();
        Map<UUID, List<UUID>> participants = loadParticipantMap(ids);
        Map<UUID, Message> lastByConv = loadLastMessages(ids);
        Map<UUID, Integer> unread = loadUnreadCounts(ids, principal.getId());
        Map<UUID, List<String>> namesByConv = loadParticipantNames(participants);
        Map<UUID, String> photosByUser = loadParticipantPhotos(participants);

        List<ConversationSummaryResponse> rows = pg.getContent().stream()
                .map(c -> mapSummary(
                        c,
                        participants.getOrDefault(c.getId(), List.of()),
                        namesByConv.getOrDefault(c.getId(), List.of()),
                        photosByUser,
                        principal.getId(),
                        lastByConv.get(c.getId()),
                        unread.getOrDefault(c.getId(), 0)
                ))
                .sorted(Comparator.comparing(
                        ConversationSummaryResponse::getLastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
        return PageData.<ConversationSummaryResponse>builder()
                .content(rows)
                .page(pg.getNumber())
                .size(pg.getSize())
                .totalElements(pg.getTotalElements())
                .totalPages(pg.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public long totalUnreadCount(UserPrincipal principal) {
        return messageRepository.countTotalUnreadForUser(principal.getId());
    }

    @Transactional
    public void markConversationRead(UserPrincipal principal, UUID conversationId) {
        assertParticipant(principal.getId(), conversationId);
        messageRepository.markConversationReadForUser(conversationId, principal.getId());
    }

    @Transactional
    public List<MessageResponse> listMessages(UserPrincipal principal, UUID conversationId) {
        assertParticipant(principal.getId(), conversationId);
        messageRepository.markConversationReadForUser(conversationId, principal.getId());
        UUID viewerId = principal.getId();
        return messageRepository.findByConversation_IdOrderBySentAtAsc(conversationId).stream()
                .map(m -> mapMessage(m, viewerId))
                .toList();
    }

    @Transactional
    public ConversationSummaryResponse createConversation(UserPrincipal principal, CreateConversationRequest req) {
        User sender = userRepository.findWithBranchById(principal.getId()).orElseThrow(() -> new BusinessException("error.user.not.found"));
        Set<UUID> recipientIds = new LinkedHashSet<>(req.getRecipientUserIds());
        recipientIds.remove(sender.getId());
        if (recipientIds.isEmpty()) {
            throw new BusinessException("error.messaging.recipients.required");
        }
        List<User> recipients = new ArrayList<>();
        for (UUID id : recipientIds) {
            User u = userRepository.findWithBranchById(id).orElseThrow(() -> new BusinessException("error.user.not.found"));
            recipients.add(u);
        }
        assertMessagingAllowed(sender, recipients);

        UUID branchId = resolveConversationBranch(sender, recipients);
        Branch branchRef = branchId != null ? Branch.builder().id(branchId).build() : null;

        Conversation conv = Conversation.builder()
                .subject(StringUtils.hasText(req.getSubject()) ? req.getSubject().trim() : null)
                .branch(branchRef)
                .build();
        conv = conversationRepository.save(conv);

        HashSet<UUID> allIds = new HashSet<>(recipientIds);
        allIds.add(sender.getId());
        Map<UUID, User> userById = new HashMap<>();
        userById.put(sender.getId(), sender);
        for (User r : recipients) {
            userById.put(r.getId(), r);
        }
        for (UUID uid : allIds) {
            User u = userById.get(uid);
            conversationParticipantRepository.save(ConversationParticipant.builder()
                    .conversation(conv)
                    .user(u)
                    .build());
        }

        postAndDispatchMessage(sender, conv, req.getFirstMessage().trim());

        try {
            auditService.log(
                    principal,
                    "CONVERSATION_CREATED",
                    conv.getId(),
                    "conversation",
                    objectMapper.writeValueAsString(Map.of("participants", allIds.stream().map(UUID::toString).toList())),
                    branchId
            );
        } catch (Exception ex) {
            log.warn("audit CONVERSATION_CREATED failed: {}", ex.getMessage());
        }

        List<UUID> pids = new ArrayList<>(allIds);
        Map<UUID, String> photos = loadParticipantPhotos(Map.of(conv.getId(), pids));
        return mapSummary(
                conv,
                pids,
                loadParticipantNames(Map.of(conv.getId(), pids)).getOrDefault(conv.getId(), List.of()),
                photos,
                principal.getId(),
                null,
                0
        );
    }

    @Transactional
    public MessageResponse postMessage(UserPrincipal principal, UUID conversationId, PostMessageRequest req) {
        User sender = userRepository.findWithBranchById(principal.getId()).orElseThrow(() -> new BusinessException("error.user.not.found"));
        Conversation conv = conversationRepository.findById(conversationId).orElseThrow(() -> new BusinessException("error.conversation.not.found"));
        assertParticipant(sender.getId(), conversationId);
        Message m = postAndDispatchMessage(sender, conv, req.getContent().trim(), req.getReplyToId());
        UUID mid = m.getId();
        try {
            auditService.log(
                    principal,
                    "MESSAGE_SENT",
                    mid,
                    "message",
                    objectMapper.writeValueAsString(Map.of("conversationId", conversationId.toString())),
                    conv.getBranch() != null ? conv.getBranch().getId() : null
            );
        } catch (Exception ex) {
            log.warn("audit MESSAGE_SENT failed: {}", ex.getMessage());
        }
        return mapMessage(messageRepository.findById(mid).orElse(m), principal.getId());
    }

    @Transactional
    public MessageResponse updateMessage(UserPrincipal principal, UUID conversationId, UUID messageId, String content) {
        assertParticipant(principal.getId(), conversationId);
        Message m = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException("error.message.not.found"));
        if (!m.getConversation().getId().equals(conversationId)) {
            throw new AccessDeniedException();
        }
        if (!m.getSender().getId().equals(principal.getId())) {
            throw new AccessDeniedException();
        }
        if (m.isDeleted()) {
            throw new BusinessException("error.message.not.found");
        }
        m.setContent(content.trim());
        m.setEditedAt(Instant.now());
        return mapMessage(messageRepository.save(m), principal.getId());
    }

    @Transactional
    public void deleteMessage(UserPrincipal principal, UUID conversationId, UUID messageId) {
        assertParticipant(principal.getId(), conversationId);
        Message m = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException("error.message.not.found"));
        if (!m.getConversation().getId().equals(conversationId)) {
            throw new AccessDeniedException();
        }
        if (!m.getSender().getId().equals(principal.getId())) {
            throw new AccessDeniedException();
        }
        m.setDeleted(true);
        messageRepository.save(m);
    }

    @Transactional
    public MessageResponse toggleReaction(
            UserPrincipal principal,
            UUID conversationId,
            UUID messageId,
            String emoji
    ) {
        assertParticipant(principal.getId(), conversationId);
        if (!StringUtils.hasText(emoji)) {
            throw new BusinessException("error.message.reaction.invalid");
        }
        String reaction = emoji.trim();
        Message m = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException("error.message.not.found"));
        if (!m.getConversation().getId().equals(conversationId) || m.isDeleted()) {
            throw new AccessDeniedException();
        }
        Map<UUID, String> reactions = parseReactions(m.getReactionsJson());
        UUID userId = principal.getId();
        String existing = reactions.get(userId);
        if (reaction.equals(existing)) {
            reactions.remove(userId);
        } else {
            reactions.put(userId, reaction);
        }
        m.setReactionsJson(serializeReactions(reactions));
        return mapMessage(messageRepository.save(m), userId);
    }

    private Message postAndDispatchMessage(User sender, Conversation conv, String text) {
        return postAndDispatchMessage(sender, conv, text, null);
    }

    private Message postAndDispatchMessage(User sender, Conversation conv, String text, UUID replyToId) {
        Message msg = Message.builder()
                .conversation(conv)
                .sender(sender)
                .content(text)
                .seen(false)
                .emailSent(false)
                .replyTo(replyToId != null ? Message.builder().id(replyToId).build() : null)
                .build();
        msg = messageRepository.save(msg);

        List<ConversationParticipant> parts = conversationParticipantRepository.findByConversation_IdOrderByJoinedAtAsc(conv.getId());
        String senderLabel = displayName(sender);
        String preview = truncate(text, 80);
        for (ConversationParticipant p : parts) {
            User u = p.getUser();
            if (u.getId().equals(sender.getId())) {
                continue;
            }
            try {
                inAppNotificationService.createForUser(
                        u.getId(),
                        senderLabel,
                        preview,
                        "INTERNAL_CHAT"
                );
            } catch (Exception ex) {
                log.warn("Could not notify user {} about internal message: {}", u.getId(), ex.getMessage());
            }
        }
        return msg;
    }

    private void assertParticipant(UUID userId, UUID conversationId) {
        if (!conversationParticipantRepository.existsByConversation_IdAndUser_Id(conversationId, userId)) {
            throw new AccessDeniedException();
        }
    }

    private Map<UUID, List<UUID>> loadParticipantMap(List<UUID> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        List<ConversationParticipant> rows = conversationParticipantRepository.findByConversation_IdIn(conversationIds);
        Map<UUID, List<UUID>> map = new HashMap<>();
        for (ConversationParticipant r : rows) {
            UUID cid = r.getConversation().getId();
            map.computeIfAbsent(cid, k -> new ArrayList<>()).add(r.getUser().getId());
        }
        return map;
    }

    private void assertMessagingAllowed(User sender, List<User> recipients) {
        Role sr = sender.getRole();
        for (User r : recipients) {
            if (!isPairAllowed(sr, sender, r)) {
                throw new BusinessException("error.messaging.invalid.recipient");
            }
        }
    }

    private boolean isPairAllowed(Role senderRole, User sender, User recipient) {
        return switch (senderRole) {
            case SUPER_ADMIN -> true;
            case GENERAL_MANAGER_PEDAGOGIQUE ->
                    recipient.getRole() == Role.SUPER_ADMIN || recipient.getRole() == Role.CENTRAL_COORDINATOR;
            case CENTRAL_COORDINATOR, LEAD_TEACHER -> {
                UUID bid = sender.getBranch() != null ? sender.getBranch().getId() : null;
                if (recipient.getRole() == Role.SUPER_ADMIN || recipient.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
                    yield true;
                }
                yield bid != null && recipient.getBranch() != null && bid.equals(recipient.getBranch().getId());
            }
            case ASSISTANT, COOK, CLEANER, TEACHER -> true;
            default -> false;
        };
    }

    private boolean isStaffMessagingTarget(UUID branchId, User recipient) {
        if (recipient.getRole() == Role.CENTRAL_COORDINATOR
                && recipient.getBranch() != null
                && branchId.equals(recipient.getBranch().getId())) {
            return true;
        }
        if (recipient.getRole() == Role.LEAD_TEACHER
                && recipient.getBranch() != null
                && branchId.equals(recipient.getBranch().getId())) {
            return true;
        }
        return false;
    }

    private UUID resolveConversationBranch(User sender, List<User> recipients) {
        Set<UUID> bids = new HashSet<>();
        if (sender.getBranch() != null) {
            bids.add(sender.getBranch().getId());
        }
        for (User u : recipients) {
            if (u.getBranch() != null) {
                bids.add(u.getBranch().getId());
            }
        }
        if (bids.size() == 1) {
            return bids.iterator().next();
        }
        return null;
    }

    private ConversationSummaryResponse mapSummary(
            Conversation c,
            List<UUID> participantIds,
            List<String> participantNames,
            Map<UUID, String> photoByUserId,
            UUID viewerUserId,
            Message lastMessage,
            int unreadCount
    ) {
        String preview = lastMessage != null ? truncate(lastMessage.getContent(), 80) : null;
        Instant lastAt = lastMessage != null ? lastMessage.getSentAt() : c.getCreatedAt();
        String avatarUrl = null;
        if (participantIds.size() == 2 && viewerUserId != null) {
            UUID peer = participantIds.stream()
                    .filter(id -> !id.equals(viewerUserId))
                    .findFirst()
                    .orElse(null);
            if (peer != null) {
                avatarUrl = photoByUserId.get(peer);
            }
        }
        return ConversationSummaryResponse.builder()
                .id(c.getId())
                .subject(c.getSubject())
                .createdAt(c.getCreatedAt())
                .branchId(c.getBranch() != null ? c.getBranch().getId() : null)
                .branchCode(c.getBranch() != null ? c.getBranch().getCode() : null)
                .participantUserIds(participantIds)
                .participantNames(participantNames)
                .group(participantIds.size() > 2)
                .lastMessagePreview(preview)
                .lastMessageAt(lastAt)
                .unreadCount(unreadCount)
                .avatarUrl(avatarUrl)
                .build();
    }

    private Map<UUID, Message> loadLastMessages(List<UUID> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Message> out = new HashMap<>();
        for (Message m : messageRepository.findByConversation_IdInOrderBySentAtDesc(conversationIds)) {
            UUID cid = m.getConversation().getId();
            out.putIfAbsent(cid, m);
        }
        return out;
    }

    private Map<UUID, Integer> loadUnreadCounts(List<UUID> conversationIds, UUID userId) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> out = new HashMap<>();
        for (Object[] row : messageRepository.countUnreadByConversation(conversationIds, userId)) {
            out.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return out;
    }

    private Map<UUID, List<String>> loadParticipantNames(Map<UUID, List<UUID>> participants) {
        Set<UUID> allIds = new HashSet<>();
        participants.values().forEach(allIds::addAll);
        Map<UUID, String> names = new HashMap<>();
        for (UUID id : allIds) {
            userRepository.findById(id).ifPresent(u -> names.put(id, displayName(u)));
        }
        Map<UUID, List<String>> out = new HashMap<>();
        for (Map.Entry<UUID, List<UUID>> e : participants.entrySet()) {
            out.put(e.getKey(), e.getValue().stream().map(names::get).filter(StringUtils::hasText).toList());
        }
        return out;
    }

    private Map<UUID, String> loadParticipantPhotos(Map<UUID, List<UUID>> participants) {
        Set<UUID> allIds = new HashSet<>();
        participants.values().forEach(allIds::addAll);
        Map<UUID, String> out = new HashMap<>();
        for (UUID id : allIds) {
            userRepository.findById(id).ifPresent(u -> {
                String photo = resolveUserPhotoUrl(u);
                if (StringUtils.hasText(photo)) {
                    out.put(id, photo);
                }
            });
        }
        return out;
    }

    private String resolveUserPhotoUrl(User u) {
        String photo = u.getProfilePhotoUrl();
        if (!StringUtils.hasText(photo)) {
            List<Employee> rows = employeeRepository.findAllByUser_Id(u.getId());
            if (!rows.isEmpty()) {
                photo = rows.get(0).getProfilePhotoUrl();
            }
        }
        return StringUtils.hasText(photo) ? photo.trim() : null;
    }

    private Map<UUID, String> parseReactions(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> raw = objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
            Map<UUID, String> out = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : raw.entrySet()) {
                try {
                    out.put(UUID.fromString(e.getKey()), e.getValue());
                } catch (Exception ignored) {
                    // skip invalid keys
                }
            }
            return out;
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private String serializeReactions(Map<UUID, String> reactions) {
        if (reactions == null || reactions.isEmpty()) {
            return null;
        }
        try {
            Map<String, String> raw = new LinkedHashMap<>();
            for (Map.Entry<UUID, String> e : reactions.entrySet()) {
                raw.put(e.getKey().toString(), e.getValue());
            }
            return objectMapper.writeValueAsString(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<MessageReactionResponse> buildReactionResponses(Map<UUID, String> reactions, UUID viewerId) {
        if (reactions == null || reactions.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Boolean> mine = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> e : reactions.entrySet()) {
            String emoji = e.getValue();
            counts.merge(emoji, 1, Integer::sum);
            if (viewerId != null && viewerId.equals(e.getKey())) {
                mine.put(emoji, true);
            }
        }
        List<MessageReactionResponse> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            out.add(MessageReactionResponse.builder()
                    .emoji(e.getKey())
                    .count(e.getValue())
                    .reactedByMe(Boolean.TRUE.equals(mine.get(e.getKey())))
                    .build());
        }
        return out;
    }

    private static String truncate(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String t = text.trim().replaceAll("\\s+", " ");
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max - 1).trim() + "…";
    }

    private MessageResponse mapMessage(Message m) {
        return mapMessage(m, null);
    }

    private MessageResponse mapMessage(Message m, UUID viewerId) {
        User s = m.getSender();
        Message reply = m.getReplyTo();
        Map<UUID, String> reactionMap = parseReactions(m.getReactionsJson());
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversation().getId())
                .senderId(s.getId())
                .senderEmail(s.getEmail())
                .senderDisplayName(displayName(s))
                .senderProfilePhotoUrl(resolveUserPhotoUrl(s))
                .content(m.getContent())
                .sentAt(m.getSentAt())
                .emailSent(m.isEmailSent())
                .emailSentAt(m.getEmailSentAt())
                .read(m.isSeen())
                .replyToId(reply != null ? reply.getId() : null)
                .replyToPreview(reply != null ? truncate(reply.getContent(), 100) : null)
                .editedAt(m.getEditedAt())
                .deleted(m.isDeleted())
                .reactions(buildReactionResponses(reactionMap, viewerId))
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
