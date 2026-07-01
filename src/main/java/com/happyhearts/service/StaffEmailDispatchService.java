package com.happyhearts.service;

import com.happyhearts.dto.response.EmailDeliveryResult;
import com.happyhearts.enums.Language;
import com.happyhearts.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Sends staff emails in an independent transaction so SMTP delivery and
 * {@code email_notifications} rows commit even if the portal inbox save rolls back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffEmailDispatchService {

    private final EmailService emailService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailDeliveryResult dispatch(
            User sender,
            String deliveryEmail,
            List<String> ccEmails,
            String subject,
            String body,
            Language lang
    ) {
        log.info("[staff-email] dispatch REQUIRES_NEW to={} subject={}", deliveryEmail, subject);
        return emailService.sendProfessionalStaffEmail(sender, deliveryEmail, ccEmails, subject, body, lang);
    }
}
