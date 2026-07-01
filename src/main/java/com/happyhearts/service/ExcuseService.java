package com.happyhearts.service;

import com.happyhearts.dto.request.CreateExcuseRequest;
import com.happyhearts.enums.Role;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.model.AttendanceExcuse;
import com.happyhearts.model.Employee;
import com.happyhearts.model.User;
import com.happyhearts.repository.AttendanceExcuseRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExcuseService {

    private final AttendanceExcuseRepository attendanceExcuseRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final EmailService emailService;
    private final StaffScopeService staffScopeService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(
            UserPrincipal principal,
            UUID employeeId,
            UUID branchId,
            LocalDate from,
            LocalDate to
    ) {
        UUID scopedEmployee = staffScopeService.resolveEmployeeFilter(principal, employeeId);
        UUID scopedBranch = branchAccessService.resolveBranchFilter(principal, branchId);
        if (principal.getRole() == Role.LEAD_TEACHER && principal.getBranchId() != null) {
            scopedBranch = principal.getBranchId();
        }
        LocalDate start = from != null ? from : LocalDate.now().minusMonths(1);
        LocalDate end = to != null ? to : LocalDate.now().plusMonths(1);
        return attendanceExcuseRepository.search(scopedEmployee, scopedBranch, start, end)
                .stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional
    public Map<String, Object> create(UserPrincipal principal, CreateExcuseRequest request) {
        Employee employee = employeeRepository.findWithBranchById(request.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
        if (principal.getRole().isStaffSelfService()) {
            staffScopeService.assertOwnEmployee(principal, employee.getId());
        } else if (principal.getRole() != Role.SUPER_ADMIN
                && principal.getRole() != Role.LEAD_TEACHER
                && principal.getRole() != Role.CENTRAL_COORDINATOR
                && principal.getRole() != Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            throw new com.happyhearts.exception.AccessDeniedException();
        } else if (principal.getRole() != Role.SUPER_ADMIN
                && principal.getRole() != Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            branchAccessService.assertBranchScope(principal, employee.getBranch().getId());
        }
        if (request.getDateFrom() == null || request.getDateTo() == null) {
            throw new BusinessException("error.excuse.dates.required");
        }
        if (request.getDateTo().isBefore(request.getDateFrom())) {
            throw new BusinessException("error.excuse.invalid.range");
        }
        if (!StringUtils.hasText(request.getReason())) {
            throw new BusinessException("error.excuse.reason.required");
        }
        if (request.getExcuseType() == null) {
            throw new BusinessException("error.excuse.type.required");
        }

        User granter = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));

        AttendanceExcuse entity = AttendanceExcuse.builder()
                .employee(employee)
                .grantedBy(granter)
                .excuseType(request.getExcuseType())
                .reason(request.getReason().trim())
                .dateFrom(request.getDateFrom())
                .dateTo(request.getDateTo())
                .build();
        AttendanceExcuse saved = attendanceExcuseRepository.save(entity);
        emailService.sendExcuseGranted(saved);
        saved.setEmailSentAt(Instant.now());
        saved = attendanceExcuseRepository.save(saved);
        return toRow(saved);
    }

    private Map<String, Object> toRow(AttendanceExcuse x) {
        Employee e = x.getEmployee();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", x.getId().toString());
        row.put("employeeId", e.getId().toString());
        row.put("fullName", e.getFirstName() + " " + e.getLastName());
        row.put("branchName", e.getBranch() != null ? e.getBranch().getName() : "");
        row.put("branchId", e.getBranch() != null ? e.getBranch().getId().toString() : "");
        row.put("excuseType", x.getExcuseType().name());
        row.put("reason", x.getReason());
        row.put("dateFrom", x.getDateFrom().toString());
        row.put("dateTo", x.getDateTo().toString());
        row.put("grantedByEmail", x.getGrantedBy().getEmail());
        row.put("createdAt", x.getCreatedAt() != null ? x.getCreatedAt().toString() : null);
        return row;
    }
}
