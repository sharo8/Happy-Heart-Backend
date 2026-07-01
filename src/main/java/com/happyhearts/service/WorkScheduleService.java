package com.happyhearts.service;

import com.happyhearts.dto.request.EmployeeWorkScheduleRequest;
import com.happyhearts.enums.GracePeriodSource;
import com.happyhearts.enums.GracePeriodStatus;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Employee;
import com.happyhearts.model.GracePeriodRequest;
import com.happyhearts.model.User;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.GracePeriodRequestRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkScheduleService {

    private final EmployeeRepository employeeRepository;
    private final WorkScheduleResolver workScheduleResolver;
    private final GracePeriodRequestRepository gracePeriodRequestRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final StaffScopeService staffScopeService;

    public UUID resolveScopedBranchId(UserPrincipal principal, UUID requestedBranchId) {
        return branchAccessService.resolveBranchFilter(principal, requestedBranchId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSchedules(UserPrincipal principal, UUID branchId) {
        UUID scoped = branchAccessService.resolveBranchFilter(principal, branchId);
        if (scoped != null) {
            branchAccessService.assertBranchScope(principal, scoped);
        } else if (!staffScopeService.isStaffSelf(principal)
                && principal.getRole() != com.happyhearts.enums.Role.SUPER_ADMIN
                && principal.getRole() != com.happyhearts.enums.Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            throw new com.happyhearts.exception.AccessDeniedException();
        }
        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(Employee::isEmploymentActive)
                .filter(e -> scoped == null || (e.getBranch() != null && scoped.equals(e.getBranch().getId())))
                .filter(e -> !staffScopeService.isStaffSelf(principal)
                        || staffScopeService.findLinkedEmployee(principal)
                        .map(linked -> linked.getId().equals(e.getId()))
                        .orElse(false))
                .sorted(Comparator
                        .comparing((Employee e) -> e.getBranch() != null ? e.getBranch().getName() : "")
                        .thenComparing(e -> e.getFirstName() + " " + e.getLastName()))
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Employee employee : employees) {
            rows.add(toScheduleRow(employee));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> updateSchedule(
            UserPrincipal principal,
            UUID employeeId,
            EmployeeWorkScheduleRequest request
    ) {
        Employee employee = employeeRepository.findWithBranchById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
        if (principal.getRole() == com.happyhearts.enums.Role.CENTRAL_COORDINATOR
                || principal.getRole() == com.happyhearts.enums.Role.LEAD_TEACHER) {
            branchAccessService.assertBranchManagementWrite(principal, employee.getBranch().getId());
        } else {
            branchAccessService.assertSuperAdminOrGmDelegatedWrite(principal);
        }

        Integer previousGrace = employee.getGracePeriodMinutes();
        boolean graceChanging = request.getGracePeriodMinutes() != null
                && !Objects.equals(previousGrace, request.getGracePeriodMinutes());

        if (graceChanging && !StringUtils.hasText(request.getGraceExplanation())) {
            throw new BusinessException("error.schedule.grace.explanation.required");
        }

        if (request.getUseBranchSchedule() != null) {
            employee.setUseBranchSchedule(request.getUseBranchSchedule());
        }
        if (request.getWorkStartTime() != null) {
            employee.setWorkStartTime(request.getWorkStartTime());
        }
        if (request.getWorkEndTime() != null) {
            employee.setWorkEndTime(request.getWorkEndTime());
        }
        if (request.getGracePeriodMinutes() != null) {
            if (request.getGracePeriodMinutes() < 0 || request.getGracePeriodMinutes() > 180) {
                throw new BusinessException("error.schedule.invalid.grace");
            }
            employee.setGracePeriodMinutes(request.getGracePeriodMinutes());
        }

        if (!employee.isUseBranchSchedule()) {
            WorkScheduleResolver.EffectiveSchedule effective = workScheduleResolver.resolve(employee);
            if (!effective.workEnd().isAfter(effective.workStart())) {
                throw new BusinessException("error.schedule.end.before.start");
            }
        }

        employeeRepository.save(employee);

        if (graceChanging) {
            User granter = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
            LocalDate today = LocalDate.now();
            GracePeriodRequest log = GracePeriodRequest.builder()
                    .employee(employee)
                    .source(GracePeriodSource.SCHEDULE_CONFIG)
                    .graceMinutes(request.getGracePeriodMinutes())
                    .status(GracePeriodStatus.APPROVED)
                    .dateFrom(today)
                    .dateTo(today)
                    .employeeReason("Work schedule grace period: " + request.getGracePeriodMinutes() + " min")
                    .approverExplanation(request.getGraceExplanation().trim())
                    .grantedBy(granter)
                    .decidedAt(Instant.now())
                    .build();
            gracePeriodRequestRepository.save(log);
        }

        return toScheduleRow(employee);
    }

    private Map<String, Object> toScheduleRow(Employee employee) {
        Branch branch = employee.getBranch();
        WorkScheduleResolver.EffectiveSchedule effective = workScheduleResolver.resolve(employee);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("employeeId", employee.getId().toString());
        row.put("fullName", employee.getFirstName() + " " + employee.getLastName());
        row.put("department", employee.getCategory() != null ? employee.getCategory().name() : "");
        row.put("branchId", branch != null ? branch.getId().toString() : "");
        row.put("branchCode", branch != null ? branch.getCode() : "");
        row.put("branchName", branch != null ? branch.getName() : "");
        row.put("photoUrl", employee.getProfilePhotoUrl());
        row.put("useBranchSchedule", employee.isUseBranchSchedule());
        row.put("workStartTime", employee.getWorkStartTime() != null ? employee.getWorkStartTime().toString() : null);
        row.put("workEndTime", employee.getWorkEndTime() != null ? employee.getWorkEndTime().toString() : null);
        row.put("gracePeriodMinutes", employee.getGracePeriodMinutes());
        row.put("branchWorkStartTime", branch != null && branch.getWorkStartTime() != null
                ? branch.getWorkStartTime().toString() : null);
        row.put("branchWorkEndTime", branch != null && branch.getWorkEndTime() != null
                ? branch.getWorkEndTime().toString() : null);
        row.put("branchGracePeriodMinutes", branch != null ? branch.getGracePeriodMinutes() : null);
        row.put("effectiveWorkStartTime", effective.workStart().toString());
        row.put("effectiveWorkEndTime", effective.workEnd().toString());
        row.put("effectiveGracePeriodMinutes", effective.graceMinutes());
        row.put("employeeOverride", effective.employeeOverride());
        return row;
    }
}
