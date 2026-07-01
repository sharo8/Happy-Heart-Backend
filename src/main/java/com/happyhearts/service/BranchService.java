package com.happyhearts.service;

import com.happyhearts.dto.request.BranchRequest;
import com.happyhearts.dto.response.BranchResponse;
import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Employee;
import com.happyhearts.model.User;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.util.RoleMapper;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.enums.Role;
import com.happyhearts.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AuditNameResolver auditNameResolver;

    @Transactional(readOnly = true)
    public List<BranchResponse> findAccessible(UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN || principal.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            return findAll();
        }
        if (principal.getRole().isBranchScopedDashboard() || principal.getRole().isStaffSelfService()) {
            UUID branchId = principal.getBranchId();
            if (branchId == null && principal.getRole().isStaffSelfService()) {
                branchId = employeeRepository.findAllByUser_Id(principal.getId()).stream()
                        .findFirst()
                        .map(e -> e.getBranch().getId())
                        .orElse(null);
            }
            if (branchId == null) {
                return List.of();
            }
            return List.of(findById(branchId));
        }
        throw new com.happyhearts.exception.AccessDeniedException();
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> findAll() {
        return branchRepository.findAllWithLeaders().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BranchResponse findById(UUID id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
        return toResponse(branch);
    }

    @Transactional
    public BranchResponse create(UserPrincipal principal, BranchRequest request) {
        branchRepository.findByCode(request.getCode()).ifPresent(b -> {
            throw new BusinessException("error.branch.code.exists");
        });
        Employee leadTeacher = resolveTeacher(request.getLeadTeacherId(), EmployeeCategory.LEAD_TEACHER);
        Employee secondTeacher = resolveTeacher(request.getSecondTeacherId(), EmployeeCategory.LEAD_TEACHER);
        validateTeacherPair(leadTeacher, secondTeacher);

        Branch branch = Branch.builder()
                .code(request.getCode())
                .name(request.getName())
                .location(request.getLocation())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .workStartTime(request.getWorkStartTime())
                .workEndTime(request.getWorkEndTime() != null ? request.getWorkEndTime() : LocalTime.of(17, 0))
                .gracePeriodMinutes(request.getGracePeriodMinutes())
                .leadTeacher(leadTeacher)
                .secondTeacher(secondTeacher)
                .createdByEmail(principal != null ? principal.getEmail() : null)
                .updatedByEmail(principal != null ? principal.getEmail() : null)
                .build();
        Branch saved = branchRepository.save(branch);
        syncBranchLeadershipPortalRoles(saved);
        return toResponse(saved);
    }

    @Transactional
    public BranchResponse update(UserPrincipal principal, UUID id, BranchRequest request) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
        branchRepository.findByCode(request.getCode()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new BusinessException("error.branch.code.exists");
            }
        });
        Employee leadTeacher = resolveTeacher(request.getLeadTeacherId(), EmployeeCategory.LEAD_TEACHER);
        Employee secondTeacher = resolveTeacher(request.getSecondTeacherId(), EmployeeCategory.LEAD_TEACHER);
        validateTeacherPair(leadTeacher, secondTeacher);
        if (leadTeacher != null && !leadTeacher.getBranch().getId().equals(branch.getId())) {
            throw new BusinessException("error.branch.lead.outside.branch");
        }
        if (secondTeacher != null && !secondTeacher.getBranch().getId().equals(branch.getId())) {
            throw new BusinessException("error.branch.second.outside.branch");
        }

        branch.setCode(request.getCode());
        branch.setName(request.getName());
        branch.setLocation(request.getLocation());
        branch.setLatitude(request.getLatitude());
        branch.setLongitude(request.getLongitude());
        branch.setWorkStartTime(request.getWorkStartTime());
        branch.setWorkEndTime(request.getWorkEndTime() != null ? request.getWorkEndTime() : LocalTime.of(17, 0));
        branch.setGracePeriodMinutes(request.getGracePeriodMinutes());
        branch.setLeadTeacher(leadTeacher);
        branch.setSecondTeacher(secondTeacher);
        branch.setUpdatedByEmail(principal != null ? principal.getEmail() : null);
        Branch saved = branchRepository.save(branch);
        syncBranchLeadershipPortalRoles(saved);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!branchRepository.existsById(id)) {
            throw new ResourceNotFoundException("error.branch.not.found");
        }
        branchRepository.deleteById(id);
    }

    private BranchResponse toResponse(Branch b) {
        return BranchResponse.builder()
                .id(b.getId())
                .code(b.getCode())
                .name(b.getName())
                .location(b.getLocation())
                .latitude(b.getLatitude())
                .longitude(b.getLongitude())
                .workStartTime(b.getWorkStartTime())
                .workEndTime(b.getWorkEndTime())
                .gracePeriodMinutes(b.getGracePeriodMinutes())
                .leadTeacherId(b.getLeadTeacher() != null ? b.getLeadTeacher().getId() : null)
                .leadTeacherName(b.getLeadTeacher() != null
                        ? (b.getLeadTeacher().getFirstName() + " " + b.getLeadTeacher().getLastName()).trim()
                        : null)
                .secondTeacherId(b.getSecondTeacher() != null ? b.getSecondTeacher().getId() : null)
                .secondTeacherName(b.getSecondTeacher() != null
                        ? (b.getSecondTeacher().getFirstName() + " " + b.getSecondTeacher().getLastName()).trim()
                        : null)
                .createdByEmail(b.getCreatedByEmail())
                .updatedByEmail(b.getUpdatedByEmail())
                .createdByName(auditNameResolver.resolveDisplayName(b.getCreatedByEmail()))
                .updatedByName(auditNameResolver.resolveDisplayName(b.getUpdatedByEmail()))
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }

    private Employee resolveTeacher(UUID id, EmployeeCategory expectedCategory) {
        if (id == null) return null;
        Employee employee = employeeRepository.findWithBranchById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
        if (employee.getCategory() != expectedCategory) {
            throw new BusinessException("error.branch.invalid.teacher.category");
        }
        return employee;
    }

    private void validateTeacherPair(Employee leadTeacher, Employee secondTeacher) {
        if (leadTeacher == null || secondTeacher == null) return;
        if (leadTeacher.getId().equals(secondTeacher.getId())) {
            throw new BusinessException("error.branch.same.teacher.role");
        }
        if (!leadTeacher.getBranch().getId().equals(secondTeacher.getBranch().getId())) {
            throw new BusinessException("error.branch.teacher.mismatch");
        }
    }

    /**
     * Business rule:
     * - Branch coordinator seat (leadTeacher) → CENTRAL_COORDINATOR.
     * - Other lead teachers in the branch → LEAD_TEACHER; other HR categories → staff roles.
     */
    private void syncBranchLeadershipPortalRoles(Branch branch) {
        UUID managerEmployeeId = branch.getLeadTeacher() != null ? branch.getLeadTeacher().getId() : null;
        Set<UUID> affectedEmployeeIds = new HashSet<>();
        if (managerEmployeeId != null) {
            affectedEmployeeIds.add(managerEmployeeId);
        }
        if (branch.getSecondTeacher() != null) {
            affectedEmployeeIds.add(branch.getSecondTeacher().getId());
        }

        for (Employee employee : employeeRepository.findAllByBranch_Id(branch.getId())) {
            if (employee.getCategory() == EmployeeCategory.LEAD_TEACHER) {
                affectedEmployeeIds.add(employee.getId());
            }
        }

        for (UUID employeeId : affectedEmployeeIds) {
            Employee employee = employeeRepository.findWithUserById(employeeId)
                    .orElseGet(() -> employeeRepository.findWithBranchById(employeeId)
                            .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found")));
            User user = resolvePortalUserForEmployee(employee);
            if (user == null) {
                continue;
            }
            boolean isManager = managerEmployeeId != null && managerEmployeeId.equals(employee.getId());
            Role newRole;
            if (isManager) {
                newRole = Role.CENTRAL_COORDINATOR;
            } else if (employee.getCategory() == EmployeeCategory.LEAD_TEACHER) {
                newRole = Role.LEAD_TEACHER;
            } else {
                newRole = RoleMapper.fromEmployeeCategory(employee.getCategory());
            }
            user.setRole(newRole);
            user.setBranch(employee.getBranch());
            userRepository.save(user);

            if (employee.getUser() == null || !employee.getUser().getId().equals(user.getId())) {
                employee.setUser(user);
                employeeRepository.save(employee);
            }
        }
    }

    private User resolvePortalUserForEmployee(Employee employee) {
        if (employee.getUser() != null) {
            return userRepository.findWithBranchById(employee.getUser().getId()).orElse(employee.getUser());
        }
        if (StringUtils.hasText(employee.getEmail())) {
            Optional<User> byEmail = userRepository.findByEmailIgnoreCase(employee.getEmail().trim());
            if (byEmail.isPresent()) {
                return byEmail.get();
            }
        }
        if (employee.getBranch() == null
                || !StringUtils.hasText(employee.getFirstName())
                || !StringUtils.hasText(employee.getLastName())) {
            return null;
        }
        return userRepository.findAllActiveWithBranchByBranchId(employee.getBranch().getId()).stream()
                .filter(u -> employee.getFirstName().trim().equalsIgnoreCase(safeTrim(u.getFirstName()))
                        && employee.getLastName().trim().equalsIgnoreCase(safeTrim(u.getLastName())))
                .findFirst()
                .orElse(null);
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
