package com.happyhearts.service;

import com.happyhearts.dto.request.CreateGracePeriodRequest;
import com.happyhearts.enums.GracePeriodStatus;
import com.happyhearts.enums.Role;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.exception.ResourceNotFoundException;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GracePeriodService {

    private final GracePeriodRequestRepository gracePeriodRequestRepository;
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
            GracePeriodStatus status,
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
        return gracePeriodRequestRepository.search(scopedEmployee, scopedBranch, status, start, end)
                .stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> eligibleRecipients(UUID branchId) {
        Set<User> users = new HashSet<>();
        users.addAll(userRepository.findAllActiveWithBranchByRoleIn(
                List.of(Role.SUPER_ADMIN, Role.LEAD_TEACHER, Role.CENTRAL_COORDINATOR)));
        return users.stream()
                .filter(u -> u.getRole() == Role.SUPER_ADMIN
                        || (u.getBranch() != null && branchId != null && branchId.equals(u.getBranch().getId())))
                .map(u -> Map.<String, Object>of(
                        "id", u.getId().toString(),
                        "email", u.getEmail(),
                        "fullName", displayName(u),
                        "role", u.getRole().name()
                ))
                .toList();
    }

    @Transactional
    public Map<String, Object> create(UserPrincipal principal, CreateGracePeriodRequest request) {
        Employee employee = employeeRepository.findWithBranchById(request.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
        assertCanManage(principal, employee);

        if (request.getDateFrom() == null || request.getDateTo() == null) {
            throw new BusinessException("error.grace.dates.required");
        }
        if (request.getDateTo().isBefore(request.getDateFrom())) {
            throw new BusinessException("error.grace.invalid.range");
        }

        boolean immediate = Boolean.TRUE.equals(request.getGrantImmediately())
                || canGrantDirectly(principal);

        GracePeriodRequest entity = GracePeriodRequest.builder()
                .employee(employee)
                .dateFrom(request.getDateFrom())
                .dateTo(request.getDateTo())
                .employeeReason(request.getEmployeeReason())
                .recipientUserIds(new HashSet<>(request.getRecipientUserIds() != null
                        ? request.getRecipientUserIds() : List.of()))
                .build();

        if (immediate) {
            if (!StringUtils.hasText(request.getApproverExplanation())) {
                throw new BusinessException("error.grace.explanation.required");
            }
            User granter = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
            entity.setStatus(GracePeriodStatus.APPROVED);
            entity.setGrantedBy(granter);
            entity.setApproverExplanation(request.getApproverExplanation().trim());
            entity.setDecidedAt(Instant.now());
        } else {
            if (!StringUtils.hasText(request.getEmployeeReason())) {
                throw new BusinessException("error.grace.reason.required");
            }
            User requester = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
            entity.setRequestedBy(requester);
            entity.setStatus(GracePeriodStatus.PENDING);
        }

        GracePeriodRequest saved = gracePeriodRequestRepository.save(entity);
        if (saved.getStatus() == GracePeriodStatus.APPROVED) {
            emailService.sendGracePeriodApproved(saved);
            saved.setEmailSentAt(Instant.now());
            saved = gracePeriodRequestRepository.save(saved);
        }
        return toRow(saved);
    }

    @Transactional
    public Map<String, Object> approve(UserPrincipal principal, UUID id, String approverExplanation) {
        if (!StringUtils.hasText(approverExplanation)) {
            throw new BusinessException("error.grace.explanation.required");
        }
        GracePeriodRequest entity = loadForDecision(principal, id);
        User granter = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
        entity.setStatus(GracePeriodStatus.APPROVED);
        entity.setGrantedBy(granter);
        entity.setApproverExplanation(approverExplanation.trim());
        entity.setDecidedAt(Instant.now());
        GracePeriodRequest saved = gracePeriodRequestRepository.save(entity);
        emailService.sendGracePeriodApproved(saved);
        saved.setEmailSentAt(Instant.now());
        return toRow(gracePeriodRequestRepository.save(saved));
    }

    @Transactional
    public Map<String, Object> reject(UserPrincipal principal, UUID id, String approverExplanation) {
        if (!StringUtils.hasText(approverExplanation)) {
            throw new BusinessException("error.grace.explanation.required");
        }
        GracePeriodRequest entity = loadForDecision(principal, id);
        User granter = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
        entity.setStatus(GracePeriodStatus.REJECTED);
        entity.setGrantedBy(granter);
        entity.setApproverExplanation(approverExplanation.trim());
        entity.setDecidedAt(Instant.now());
        return toRow(gracePeriodRequestRepository.save(entity));
    }

    private GracePeriodRequest loadForDecision(UserPrincipal principal, UUID id) {
        GracePeriodRequest entity = gracePeriodRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.grace.not.found"));
        assertCanManage(principal, entity.getEmployee());
        if (entity.getStatus() != GracePeriodStatus.PENDING) {
            throw new BusinessException("error.grace.already.decided");
        }
        return entity;
    }

    private void assertCanManage(UserPrincipal principal, Employee employee) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() == Role.LEAD_TEACHER || principal.getRole() == Role.CENTRAL_COORDINATOR) {
            branchAccessService.assertBranchScope(principal, employee.getBranch().getId());
            return;
        }
        if (isStaffSelf(principal, employee)) {
            return;
        }
        throw new com.happyhearts.exception.AccessDeniedException();
    }

    private boolean isStaffSelf(UserPrincipal principal, Employee employee) {
        return StringUtils.hasText(employee.getEmail())
                && employee.getEmail().equalsIgnoreCase(principal.getEmail());
    }

    private boolean canGrantDirectly(UserPrincipal principal) {
        return principal.getRole() == Role.SUPER_ADMIN
                || principal.getRole() == Role.LEAD_TEACHER
                || principal.getRole() == Role.CENTRAL_COORDINATOR;
    }

    private Map<String, Object> toRow(GracePeriodRequest g) {
        Employee e = g.getEmployee();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", g.getId().toString());
        row.put("employeeId", e.getId().toString());
        row.put("fullName", e.getFirstName() + " " + e.getLastName());
        row.put("branchName", e.getBranch() != null ? e.getBranch().getName() : "");
        row.put("branchId", e.getBranch() != null ? e.getBranch().getId().toString() : "");
        row.put("employeeReason", g.getEmployeeReason());
        row.put("approverExplanation", g.getApproverExplanation());
        row.put("status", g.getStatus().name().toLowerCase());
        row.put("source", g.getSource() != null ? g.getSource().name().toLowerCase() : "attendance");
        row.put("graceMinutes", g.getGraceMinutes());
        row.put("dateFrom", g.getDateFrom().toString());
        row.put("dateTo", g.getDateTo().toString());
        row.put("recipientUserIds", g.getRecipientUserIds().stream().map(UUID::toString).toList());
        row.put("requestedByEmail", g.getRequestedBy() != null ? g.getRequestedBy().getEmail() : null);
        row.put("grantedByEmail", g.getGrantedBy() != null ? g.getGrantedBy().getEmail() : null);
        row.put("createdAt", g.getCreatedAt() != null ? g.getCreatedAt().toString() : null);
        row.put("decidedAt", g.getDecidedAt() != null ? g.getDecidedAt().toString() : null);
        return row;
    }

    private String displayName(User u) {
        String name = ((u.getFirstName() != null ? u.getFirstName() : "") + " "
                + (u.getLastName() != null ? u.getLastName() : "")).trim();
        return name.isEmpty() ? u.getEmail() : name;
    }
}
