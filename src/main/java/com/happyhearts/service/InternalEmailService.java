package com.happyhearts.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.happyhearts.dto.PageData;
import com.happyhearts.dto.request.ReplyInternalEmailRequest;
import com.happyhearts.dto.request.SaveEmailDraftRequest;
import com.happyhearts.dto.request.SendInternalEmailRequest;
import com.happyhearts.dto.response.EmailDeliveryResult;
import com.happyhearts.dto.response.InternalEmailResponse;
import com.happyhearts.exception.AccessDeniedException;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.model.Employee;
import com.happyhearts.model.InternalEmail;
import com.happyhearts.model.User;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.InternalEmailRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InternalEmailService {

    private final InternalEmailRepository internalEmailRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final StaffEmailDispatchService staffEmailDispatchService;
    private final InAppNotificationService inAppNotificationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageData<InternalEmailResponse> list(
            UserPrincipal principal,
            String folder,
            String q,
            int page,
            int size
    ) {
        String f = normalizeFolder(folder);
        String query = q != null ? q.trim() : "";
        Page<InternalEmail> pg = internalEmailRepository.searchForUser(
                principal.getId(),
                f,
                query,
                PageRequest.of(page, Math.min(size, 100))
        );
        return PageData.from(pg.map(this::mapRow));
    }

    @Transactional(readOnly = true)
    public long unreadInboxCount(UserPrincipal principal) {
        return internalEmailRepository.countByToUser_IdAndFolderAndReadFalse(principal.getId(), "inbox");
    }

    @Transactional(readOnly = true)
    public InternalEmailResponse get(UserPrincipal principal, UUID id) {
        InternalEmail row = loadOwned(id, principal.getId());
        return mapRow(row);
    }

    @Transactional(readOnly = true)
    public List<InternalEmailResponse> thread(UserPrincipal principal, UUID id) {
        InternalEmail row = loadOwned(id, principal.getId());
        UUID threadId = row.getThreadId() != null ? row.getThreadId() : row.getId();
        String viewerFolder = row.getFolder();
        return internalEmailRepository.findByThreadIdOrderBySentAtAsc(threadId).stream()
                .filter(e -> canAccess(principal.getId(), e))
                .filter(e -> matchesViewerCopy(principal.getId(), e, viewerFolder))
                .map(this::mapRow)
                .toList();
    }

    @Transactional
    public InternalEmailResponse send(UserPrincipal principal, SendInternalEmailRequest req) {
        User sender = userRepository.findWithBranchById(principal.getId())
                .orElseThrow(() -> new BusinessException("error.user.not.found"));
        Set<UUID> toIds = new LinkedHashSet<>(req.getToUserIds());
        if (toIds.isEmpty()) {
            throw new BusinessException("error.email.recipients.required");
        }
        String subject = req.getSubject().trim();
        String body = req.getBody().trim();
        String ccJson = serializeCc(req.getCcUserIds());
        UUID threadId = resolveThreadId(req.getReplyToId(), req.getDraftId());
        Instant now = Instant.now();
        String label = StringUtils.hasText(req.getLabel()) ? req.getLabel().trim() : null;

        if (req.getDraftId() != null) {
            internalEmailRepository.findById(req.getDraftId()).ifPresent(d -> {
                if (d.getFromUser().getId().equals(sender.getId()) && "drafts".equals(d.getFolder())) {
                    internalEmailRepository.delete(d);
                }
            });
        }

        InternalEmailResponse last = null;
        List<String> ccEmails = resolveCcEmails(req.getCcUserIds());
        boolean anyFailed = false;
        for (UUID toId : toIds) {
            User recipient = userRepository.findWithBranchById(toId)
                    .orElseThrow(() -> new BusinessException("error.user.not.found"));
            String deliveryEmail = resolveDeliveryEmail(recipient);
            if (!StringUtils.hasText(deliveryEmail)) {
                throw new BusinessException("error.employee.email.missing");
            }
            log.info(
                    "[staff-email] send start sender={} recipientUser={} deliveryEmail={} subject={}",
                    sender.getEmail(),
                    recipient.getId(),
                    deliveryEmail,
                    subject
            );
            EmailDeliveryResult delivery = staffEmailDispatchService.dispatch(
                    sender,
                    deliveryEmail,
                    ccEmails,
                    subject,
                    body,
                    recipient.getPreferredLanguage()
            );
            String statusLabel = delivery.isSent() ? "SENT" : "FAILED";
            if (!delivery.isSent()) {
                anyFailed = true;
                log.error(
                        "[staff-email] delivery failed sender={} to={} notificationId={} error={}",
                        sender.getEmail(),
                        deliveryEmail,
                        delivery.getNotificationId(),
                        delivery.getErrorMessage()
                );
            }
            InternalEmail inbox = InternalEmail.builder()
                    .fromUser(sender)
                    .toUser(recipient)
                    .ccUserIds(ccJson)
                    .subject(subject)
                    .body(body)
                    .folder("inbox")
                    .read(false)
                    .starred(false)
                    .label(label)
                    .threadId(threadId)
                    .replyTo(req.getReplyToId() != null ? InternalEmail.builder().id(req.getReplyToId()).build() : null)
                    .sentAt(now)
                    .deliveryEmail(deliveryEmail)
                    .build();
            inbox = internalEmailRepository.save(inbox);
            if (threadId == null) {
                threadId = inbox.getId();
                inbox.setThreadId(threadId);
                inbox = internalEmailRepository.save(inbox);
            }

            InternalEmail sent = InternalEmail.builder()
                    .fromUser(sender)
                    .toUser(recipient)
                    .ccUserIds(ccJson)
                    .subject(subject)
                    .body(body)
                    .folder("sent")
                    .read(true)
                    .starred(false)
                    .label(label)
                    .threadId(threadId)
                    .replyTo(req.getReplyToId() != null ? InternalEmail.builder().id(req.getReplyToId()).build() : null)
                    .sentAt(now)
                    .smtpStatus(statusLabel)
                    .deliveryEmail(deliveryEmail)
                    .smtpProvider(delivery.getProvider())
                    .outboundSubject(delivery.getOutboundSubject())
                    .emailNotificationId(delivery.getNotificationId())
                    .smtpError(delivery.getErrorMessage())
                    .build();
            sent = internalEmailRepository.save(sent);
            try {
                inAppNotificationService.createForUser(
                        recipient.getId(),
                        sender.getFirstName() != null ? displayName(sender) : sender.getEmail(),
                        truncate(subject, 80),
                        "STAFF_EMAIL"
                );
            } catch (Exception ex) {
                log.warn("Could not notify user {} about staff email: {}", recipient.getId(), ex.getMessage());
            }
            last = mapRow(sent);
        }
        if (anyFailed) {
            log.warn("[staff-email] send completed with SMTP failure(s) sender={}", sender.getEmail());
        }
        if (last == null) {
            throw new BusinessException("error.email.recipients.required");
        }
        return last;
    }

    private List<String> resolveCcEmails(List<UUID> ccUserIds) {
        if (ccUserIds == null || ccUserIds.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (UUID id : ccUserIds) {
            userRepository.findById(id).map(this::resolveDeliveryEmail).ifPresent(out::add);
        }
        return out;
    }

    private String resolveDeliveryEmail(User recipient) {
        if (StringUtils.hasText(recipient.getEmail())) {
            return recipient.getEmail().trim();
        }
        List<Employee> employees = employeeRepository.findAllByUser_Id(recipient.getId());
        for (Employee emp : employees) {
            if (StringUtils.hasText(emp.getEmail())) {
                return emp.getEmail().trim();
            }
        }
        return null;
    }

    @Transactional
    public InternalEmailResponse saveDraft(UserPrincipal principal, SaveEmailDraftRequest req) {
        User sender = userRepository.findWithBranchById(principal.getId())
                .orElseThrow(() -> new BusinessException("error.user.not.found"));
        UUID toId = req.getToUserIds() != null && !req.getToUserIds().isEmpty()
                ? req.getToUserIds().get(0)
                : sender.getId();
        User toUser = userRepository.findById(toId).orElse(sender);
        InternalEmail draft;
        if (req.getDraftId() != null) {
            draft = internalEmailRepository.findById(req.getDraftId())
                    .orElseThrow(() -> new BusinessException("error.email.not.found"));
            if (!draft.getFromUser().getId().equals(sender.getId()) || !"drafts".equals(draft.getFolder())) {
                throw new AccessDeniedException();
            }
            draft.setToUser(toUser);
            draft.setCcUserIds(serializeCc(req.getCcUserIds()));
            draft.setSubject(req.getSubject() != null ? req.getSubject().trim() : "");
            draft.setBody(req.getBody() != null ? req.getBody().trim() : "");
        } else {
            draft = InternalEmail.builder()
                    .fromUser(sender)
                    .toUser(toUser)
                    .ccUserIds(serializeCc(req.getCcUserIds()))
                    .subject(req.getSubject() != null ? req.getSubject().trim() : "")
                    .body(req.getBody() != null ? req.getBody().trim() : "")
                    .folder("drafts")
                    .read(true)
                    .build();
        }
        return mapRow(internalEmailRepository.save(draft));
    }

    @Transactional
    public InternalEmailResponse reply(UserPrincipal principal, UUID id, ReplyInternalEmailRequest req) {
        InternalEmail original = loadOwned(id, principal.getId());
        User sender = userRepository.findWithBranchById(principal.getId())
                .orElseThrow(() -> new BusinessException("error.user.not.found"));
        UUID threadId = original.getThreadId() != null ? original.getThreadId() : original.getId();
        String subject = original.getSubject().startsWith("Re:")
                ? original.getSubject()
                : "Re: " + original.getSubject();
        User recipient = original.getFromUser().getId().equals(sender.getId())
                ? original.getToUser()
                : original.getFromUser();

        SendInternalEmailRequest sendReq = new SendInternalEmailRequest();
        sendReq.setToUserIds(List.of(recipient.getId()));
        sendReq.setSubject(subject);
        sendReq.setBody(req.getBody().trim());
        sendReq.setReplyToId(original.getId());
        sendReq.setLabel(original.getLabel());
        return send(principal, sendReq);
    }

    @Transactional
    public InternalEmailResponse markRead(UserPrincipal principal, UUID id, boolean read) {
        InternalEmail row = loadOwned(id, principal.getId());
        row.setRead(read);
        return mapRow(internalEmailRepository.save(row));
    }

    @Transactional
    public InternalEmailResponse toggleStar(UserPrincipal principal, UUID id, boolean starred) {
        InternalEmail row = loadOwned(id, principal.getId());
        row.setStarred(starred);
        return mapRow(internalEmailRepository.save(row));
    }

    @Transactional
    public InternalEmailResponse setLabel(UserPrincipal principal, UUID id, String label) {
        InternalEmail row = loadOwned(id, principal.getId());
        row.setLabel(StringUtils.hasText(label) ? label.trim() : null);
        return mapRow(internalEmailRepository.save(row));
    }

    @Transactional
    public InternalEmailResponse moveToFolder(UserPrincipal principal, UUID id, String folder) {
        InternalEmail row = loadOwned(id, principal.getId());
        row.setFolder(normalizeFolder(folder));
        return mapRow(internalEmailRepository.save(row));
    }

    @Transactional
    public void recordExplanationSent(UserPrincipal principal, User employeeUser, String subject, String body) {
        if (employeeUser == null) {
            return;
        }
        try {
            User sender = principal != null
                    ? userRepository.findById(principal.getId()).orElse(null)
                    : null;
            if (sender == null) {
                return;
            }
            Instant now = Instant.now();
            InternalEmail sent = InternalEmail.builder()
                    .fromUser(sender)
                    .toUser(employeeUser)
                    .subject(subject)
                    .body(body)
                    .folder("sent")
                    .read(true)
                    .label("DEMANDE_EXPLICATION")
                    .sentAt(now)
                    .build();
            sent = internalEmailRepository.save(sent);
            sent.setThreadId(sent.getId());
            internalEmailRepository.save(sent);

            InternalEmail inbox = InternalEmail.builder()
                    .fromUser(sender)
                    .toUser(employeeUser)
                    .subject(subject)
                    .body(body)
                    .folder("inbox")
                    .label("DEMANDE_EXPLICATION")
                    .threadId(sent.getThreadId())
                    .sentAt(now)
                    .build();
            internalEmailRepository.save(inbox);
        } catch (Exception ex) {
            log.warn("Could not record explanation email in internal_emails: {}", ex.getMessage());
        }
    }

    private InternalEmail loadOwned(UUID id, UUID userId) {
        InternalEmail row = internalEmailRepository.findWithUsersById(id)
                .orElseThrow(() -> new BusinessException("error.email.not.found"));
        if (!canAccess(userId, row)) {
            throw new AccessDeniedException();
        }
        return row;
    }

    private boolean canAccess(UUID userId, InternalEmail e) {
        return e.getFromUser().getId().equals(userId) || e.getToUser().getId().equals(userId);
    }

    private boolean matchesViewerCopy(UUID viewerId, InternalEmail e, String viewerFolder) {
        if ("sent".equals(viewerFolder) || "drafts".equals(viewerFolder)) {
            return e.getFromUser().getId().equals(viewerId) && viewerFolder.equals(e.getFolder());
        }
        return e.getToUser().getId().equals(viewerId) && viewerFolder.equals(e.getFolder());
    }

    private UUID resolveThreadId(UUID replyToId, UUID draftId) {
        if (replyToId == null) {
            return null;
        }
        return internalEmailRepository.findById(replyToId)
                .map(e -> e.getThreadId() != null ? e.getThreadId() : e.getId())
                .orElse(null);
    }

    private String serializeCc(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<UUID> parseCc(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<UUID>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String normalizeFolder(String folder) {
        if (!StringUtils.hasText(folder)) {
            return "inbox";
        }
        String f = folder.trim().toLowerCase();
        return switch (f) {
            case "inbox", "sent", "drafts", "spam", "trash" -> f;
            default -> "inbox";
        };
    }

    private InternalEmailResponse mapRow(InternalEmail e) {
        User from = e.getFromUser();
        User to = e.getToUser();
        UUID threadId = e.getThreadId() != null ? e.getThreadId() : e.getId();
        int threadCount = internalEmailRepository.findByThreadIdOrderBySentAtAsc(threadId).size();
        String preview = truncate(e.getBody(), 120);
        String deliveryEmail = StringUtils.hasText(e.getDeliveryEmail()) ? e.getDeliveryEmail() : to.getEmail();
        return InternalEmailResponse.builder()
                .id(e.getId())
                .fromUserId(from.getId())
                .fromDisplayName(displayName(from))
                .fromEmail(from.getEmail())
                .toUserId(to.getId())
                .toDisplayName(displayName(to))
                .toEmail(to.getEmail())
                .deliveredToEmail(deliveryEmail)
                .ccUserIds(parseCc(e.getCcUserIds()))
                .subject(e.getSubject())
                .body(e.getBody())
                .bodyPreview(preview)
                .folder(e.getFolder())
                .read(e.isRead())
                .starred(e.isStarred())
                .label(e.getLabel())
                .threadId(threadId)
                .replyToId(e.getReplyTo() != null ? e.getReplyTo().getId() : null)
                .sentAt(e.getSentAt())
                .createdAt(e.getCreatedAt())
                .threadCount(threadCount)
                .smtpStatus(e.getSmtpStatus())
                .smtpProvider(e.getSmtpProvider())
                .outboundSubject(e.getOutboundSubject())
                .emailNotificationId(e.getEmailNotificationId())
                .smtpErrorMessage(e.getSmtpError())
                .build();
    }

    private static String displayName(User u) {
        String fn = u.getFirstName() != null ? u.getFirstName().trim() : "";
        String ln = u.getLastName() != null ? u.getLastName().trim() : "";
        String both = (fn + " " + ln).trim();
        return both.isEmpty() ? u.getEmail() : both;
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
}
