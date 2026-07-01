package com.happyhearts.service;

import com.happyhearts.config.AttendanceProperties;
import com.happyhearts.dto.PageData;
import com.happyhearts.dto.response.EmailNotificationResponse;
import com.happyhearts.enums.NotificationStatus;
import com.happyhearts.enums.NotificationType;
import com.happyhearts.enums.Role;
import com.happyhearts.enums.ScanType;
import com.happyhearts.model.AttendanceRecord;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Employee;
import com.happyhearts.model.User;
import com.happyhearts.repository.AttendanceRecordRepository;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.EmailNotificationRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmailNotificationRepository emailNotificationRepository;
    private final EmailService emailService;
    private final InAppNotificationService inAppNotificationService;
    private final BranchAccessService branchAccessService;
    private final AttendanceProperties attendanceProperties;

    @Transactional(readOnly = true)
    public void triggerDailyReport(UserPrincipal principal, UUID branchId, LocalDate date) {
        branchAccessService.assertBranchScope(principal, branchId);
        sendDailyReportForBranch(branchId, date);
    }

    @Transactional
    public void sendDailyReportForBranch(UUID branchId, LocalDate date) {
        Branch branch = branchRepository.findById(branchId).orElseThrow();
        Set<User> recipients = new LinkedHashSet<>();
        recipients.addAll(userRepository.findByBranch_IdAndRole(branchId, Role.CENTRAL_COORDINATOR));
        recipients.addAll(userRepository.findByBranch_IdAndRole(branchId, Role.LEAD_TEACHER));
        if (recipients.isEmpty()) {
            return;
        }
        Instant start = TimeUtils.startOfDayKigali(date);
        Instant end = TimeUtils.endOfDayKigali(date);
        List<AttendanceRecord> records = attendanceRecordRepository
                .findByBranch_IdAndScannedAtBetweenOrderByScannedAtAsc(branchId, start, end);

        Map<UUID, List<AttendanceRecord>> byEmployee = records.stream()
                .collect(Collectors.groupingBy(r -> r.getEmployee().getId()));

        List<Employee> employees = employeeRepository.findByBranch(branch);
        List<String> present = new ArrayList<>();
        List<String> absent = new ArrayList<>();
        List<String> late = new ArrayList<>();

        for (Employee employee : employees) {
            String name = employee.getFirstName() + " " + employee.getLastName();
            List<AttendanceRecord> empRecs = byEmployee.getOrDefault(employee.getId(), List.of());
            List<AttendanceRecord> entries = empRecs.stream()
                    .filter(r -> r.getScanType() == ScanType.ENTRY)
                    .sorted(Comparator.comparing(AttendanceRecord::getScannedAt))
                    .toList();
            if (entries.isEmpty()) {
                absent.add(name);
                continue;
            }
            present.add(name);
            AttendanceRecord first = entries.get(0);
            if (isLateEntry(employee.getBranch(), first.getScannedAt())) {
                late.add(name);
            }
        }

        for (User manager : recipients) {
            emailService.sendDailyReport(manager, branch, date, present, absent, late);
            String title = "Happy Hearts [" + branch.getCode() + "] — " + date;
            String body = String.format(
                    "Present: %d · Absent: %d · Late: %d|META|%s|%s",
                    present.size(),
                    absent.size(),
                    late.size(),
                    branchId,
                    date
            );
            inAppNotificationService.createForUser(manager.getId(), title, body, "DAILY_REPORT");
        }
    }

    @Transactional(readOnly = true)
    public void sendDailyReportsForAllBranches(LocalDate date) {
        branchRepository.findAll().forEach(b -> sendDailyReportForBranch(b.getId(), date));
    }

    @Transactional(readOnly = true)
    public PageData<EmailNotificationResponse> logs(
            UserPrincipal principal,
            int page,
            int size,
            NotificationStatus status,
            NotificationType type
    ) {
        branchAccessService.assertSuperAdmin(principal);
        Pageable pageable = PageRequest.of(page, size);
        Page<com.happyhearts.model.EmailNotification> result;
        if (status != null && type != null) {
            result = emailNotificationRepository.findByStatusAndNotificationType(status, type, pageable);
        } else if (status != null) {
            result = emailNotificationRepository.findByStatus(status, pageable);
        } else if (type != null) {
            result = emailNotificationRepository.findByNotificationType(type, pageable);
        } else {
            result = emailNotificationRepository.findAll(pageable);
        }
        return PageData.from(result.map(this::toResponse));
    }

    @Transactional
    public void sendAbsenceAlerts(LocalDate date) {
        branchRepository.findAll().forEach(branch -> {
            Instant dayStart = TimeUtils.startOfDayKigali(date);
            ZonedDateTime ten = date.atTime(10, 0).atZone(TimeUtils.kigali());
            Instant cutoff = ten.toInstant();
            List<Employee> employees = employeeRepository.findByBranch(branch);
            for (Employee employee : employees) {
                boolean hasEntry = attendanceRecordRepository.existsByEmployee_IdAndBranch_IdAndScanTypeAndScannedAtBetween(
                        employee.getId(), branch.getId(), ScanType.ENTRY, dayStart, cutoff);
                if (!hasEntry) {
                    emailService.sendAbsenceAlert(employee, date);
                }
            }
        });
    }

    private boolean isLateEntry(Branch branch, Instant scannedAt) {
        ZonedDateTime z = scannedAt.atZone(TimeUtils.kigali());
        LocalTime arrival = z.toLocalTime();
        LocalTime start = branch.getWorkStartTime() != null
                ? branch.getWorkStartTime()
                : attendanceProperties.getWorkStartTime();
        int grace = branch.getGracePeriodMinutes() != null
                ? branch.getGracePeriodMinutes()
                : attendanceProperties.getGracePeriodMinutes();
        LocalTime threshold = start.plusMinutes(grace);
        return arrival.isAfter(threshold);
    }

    private EmailNotificationResponse toResponse(com.happyhearts.model.EmailNotification n) {
        return EmailNotificationResponse.builder()
                .id(n.getId())
                .recipientEmail(n.getRecipientEmail())
                .subject(n.getSubject())
                .language(n.getLanguage())
                .notificationType(n.getNotificationType())
                .status(n.getStatus())
                .sentAt(n.getSentAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
