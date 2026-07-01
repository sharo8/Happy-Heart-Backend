package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.PageData;
import com.happyhearts.dto.request.CreateConversationRequest;
import com.happyhearts.dto.request.PostMessageRequest;
import com.happyhearts.dto.request.ToggleMessageReactionRequest;
import com.happyhearts.dto.request.UpdateMessageRequest;
import com.happyhearts.dto.response.ConversationSummaryResponse;
import com.happyhearts.dto.response.FeedbackRecipientOptionResponse;
import com.happyhearts.dto.response.MessageResponse;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.MessagingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class MessagingController {

    private final MessagingService messagingService;
    private final MessageSource messageSource;

    @GetMapping
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<PageData<ConversationSummaryResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageData<ConversationSummaryResponse> data = messagingService.listConversations(principal, page, Math.min(size, 100));
        String msg = messageSource.getMessage("messaging.list.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/recipient-suggestions")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<List<FeedbackRecipientOptionResponse>>> messagingRecipients(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<FeedbackRecipientOptionResponse> data = messagingService.messagingRecipientSuggestions(principal);
        String msg = messageSource.getMessage("messaging.recipients.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/unread-count")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        long count = messagingService.totalUnreadCount(principal);
        String msg = messageSource.getMessage("messaging.unread.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, Map.of("total", count)));
    }

    @PostMapping
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<ConversationSummaryResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateConversationRequest request
    ) {
        ConversationSummaryResponse data = messagingService.createConversation(principal, request);
        String msg = messageSource.getMessage("messaging.create.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<List<MessageResponse>>> messages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID conversationId
    ) {
        List<MessageResponse> data = messagingService.listMessages(principal, conversationId);
        String msg = messageSource.getMessage("messaging.messages.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<MessageResponse>> postMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID conversationId,
            @Valid @RequestBody PostMessageRequest request
    ) {
        MessageResponse data = messagingService.postMessage(principal, conversationId, request);
        String msg = messageSource.getMessage("messaging.message.sent", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PutMapping("/{conversationId}/messages/{messageId}")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<MessageResponse>> updateMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            @Valid @RequestBody UpdateMessageRequest request
    ) {
        MessageResponse data = messagingService.updateMessage(principal, conversationId, messageId, request.getContent());
        String msg = messageSource.getMessage("messaging.message.updated", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId
    ) {
        messagingService.deleteMessage(principal, conversationId, messageId);
        String msg = messageSource.getMessage("messaging.message.deleted", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }

    @PutMapping("/{conversationId}/messages/{messageId}/reactions")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<MessageResponse>> toggleReaction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            @Valid @RequestBody ToggleMessageReactionRequest request
    ) {
        MessageResponse data = messagingService.toggleReaction(
                principal,
                conversationId,
                messageId,
                request.getEmoji()
        );
        String msg = messageSource.getMessage("messaging.reaction.updated", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }
}
