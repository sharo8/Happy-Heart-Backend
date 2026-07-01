package com.happyhearts.service;

import com.happyhearts.config.AttendanceProperties;
import com.happyhearts.enums.ExplanationRequestStatus;
import com.happyhearts.enums.NotificationStatus;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.model.Employee;
import com.happyhearts.model.ExplanationRequest;
import com.happyhearts.model.User;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.ExplanationRequestRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExplanationRequestService {

    private final ExplanationRequestRepository explanationRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AttendanceProperties attendanceProperties;
    private final InternalEmailService internalEmailService;

    @Transactional(readOnly = true)
    public Set<UUID> employeeIdsWithRequestToday() {
        LocalDate today = LocalDate.now(TimeUtils.kigali());
        Instant dayStart = TimeUtils.startOfDayKigali(today);
        Instant dayEnd = TimeUtils.endOfDayKigali(today);
        return explanationRequestRepository.findEmployeeIdsWithRequestBetween(dayStart, dayEnd)
                .stream()
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean hasRequestToday(UUID employeeId) {
        LocalDate today = LocalDate.now(TimeUtils.kigali());
        return explanationRequestRepository.existsByEmployee_IdAndSentAtBetween(
                employeeId,
                TimeUtils.startOfDayKigali(today),
                TimeUtils.endOfDayKigali(today));
    }

    @Transactional
    public ExplanationRequest sendExplanationEmail(
            UserPrincipal principal,
            UUID employeeId,
            String to,
            String subject,
            String body
    ) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException("error.employee.not.found"));

        User admin = null;
        if (principal != null) {
            admin = userRepository.findById(principal.getId()).orElse(null);
        }
        String recipient = StringUtils.hasText(to) ? to.trim() : employee.getEmail();
        if (!StringUtils.hasText(recipient)) {
            throw new BusinessException("error.employee.email.missing");
        }

        NotificationStatus status = emailService.sendExplanationRequest(recipient, subject, body, employee);
        if (status != NotificationStatus.SENT) {
            throw new BusinessException("error.explanation.email.failed");
        }

        ExplanationRequest request = ExplanationRequest.builder()
                .employee(employee)
                .admin(admin)
                .reason(body)
                .subject(subject)
                .sentAt(Instant.now())
                .status(ExplanationRequestStatus.SENT)
                .build();
        try {
            ExplanationRequest saved = explanationRequestRepository.save(request);
            if (employee.getUser() != null) {
                internalEmailService.recordExplanationSent(principal, employee.getUser(), subject, body);
            }
            return saved;
        } catch (Exception ex) {
            log.error("Email sent but could not persist explanation_requests row for employee {}", employeeId, ex);
            return request;
        }
    }

    public boolean exceedsCheckoutThreshold(int checkOutCount) {
        return checkOutCount > attendanceProperties.getMaxCheckoutsPerDay();
    }
}
