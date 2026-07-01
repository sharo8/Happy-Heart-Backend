package com.happyhearts.service;

import com.happyhearts.model.Branch;
import com.happyhearts.model.Conversation;
import com.happyhearts.model.Employee;
import com.happyhearts.model.Feedback;
import com.happyhearts.model.Message;
import com.happyhearts.model.User;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.ConversationRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.FeedbackRepository;
import com.happyhearts.repository.MessageRepository;
import com.happyhearts.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditTargetResolver {

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final FeedbackRepository feedbackRepository;
    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final ConversationRepository conversationRepository;

    @Transactional(readOnly = true)
    public String resolveLabel(String targetType, UUID targetId) {
        if (targetId == null || !StringUtils.hasText(targetType)) {
            return null;
        }
        return switch (targetType.trim().toLowerCase()) {
            case "user" -> userRepository.findById(targetId).map(this::formatUser).orElse(null);
            case "message" -> messageRepository.findById(targetId)
                    .map(m -> "« " + truncate(m.getContent(), 72) + " »")
                    .orElse(null);
            case "feedback" -> feedbackRepository.findById(targetId)
                    .map(f -> formatFeedback(f))
                    .orElse(null);
            case "employee" -> employeeRepository.findById(targetId)
                    .map(e -> (e.getFirstName() + " " + e.getLastName()).trim())
                    .orElse(null);
            case "branch" -> branchRepository.findById(targetId).map(Branch::getName).orElse(null);
            case "conversation" -> conversationRepository.findById(targetId)
                    .map(this::formatConversation)
                    .orElse(null);
            default -> null;
        };
    }

    private String formatUser(User user) {
        String name = ((StringUtils.hasText(user.getFirstName()) ? user.getFirstName().trim() : "")
                + " "
                + (StringUtils.hasText(user.getLastName()) ? user.getLastName().trim() : "")).trim();
        if (StringUtils.hasText(name) && StringUtils.hasText(user.getEmail())) {
            return name + " (" + user.getEmail().trim() + ")";
        }
        if (StringUtils.hasText(name)) {
            return name;
        }
        return StringUtils.hasText(user.getEmail()) ? user.getEmail().trim() : null;
    }

    private String formatFeedback(Feedback feedback) {
        String preview = truncate(feedback.getContent(), 56);
        return feedback.getType().name() + " — « " + preview + " »";
    }

    private String formatConversation(Conversation conversation) {
        if (StringUtils.hasText(conversation.getSubject())) {
            return conversation.getSubject().trim();
        }
        return "Conversation";
    }

    private static String truncate(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return "—";
        }
        String t = text.trim().replaceAll("\\s+", " ");
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max - 1).trim() + "…";
    }
}
