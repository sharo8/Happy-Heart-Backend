package com.happyhearts.service;

import com.happyhearts.dto.PageData;
import com.happyhearts.dto.request.AssignRfidRequest;
import com.happyhearts.dto.request.CreateEmployeeRequest;
import com.happyhearts.dto.request.UpdateEmployeeRequest;
import com.happyhearts.dto.request.UpdateEmploymentActiveRequest;
import com.happyhearts.dto.response.EmployeeResponse;
import com.happyhearts.dto.response.RfidStatusResponse;
import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.enums.Role;
import com.happyhearts.exception.AccessDeniedException;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.mapper.EmployeeMapper;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Employee;
import com.happyhearts.model.User;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.util.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final EmployeeMapper employeeMapper;
    private final BranchAccessService branchAccessService;
    private final EmailService emailService;
    private final AuditNameResolver auditNameResolver;
    private final InAppNotificationService inAppNotificationService;
    private final UserRepository userRepository;

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Ensures the email is not already used by another employee or an unrelated portal account.
     */
    private void assertEmployeeEmailAvailable(String rawEmail, UUID employeeId) {
        String email = trimToNull(rawEmail);
        if (email == null) {
            return;
        }
        if (employeeRepository.existsByNormalizedEmail(email, employeeId)) {
            throw new BusinessException("error.employee.email.exists");
        }
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            UUID linkedUserId = null;
            if (employeeId != null) {
                linkedUserId = employeeRepository.findWithUserById(employeeId)
                        .map(Employee::getUser)
                        .map(User::getId)
                        .orElse(null);
            }
            if (linkedUserId != null && user.getId().equals(linkedUserId)) {
                return;
            }
            throw new BusinessException("error.employee.email.used.by.portal");
        });
    }

    private void notifyStaffWatchers(UUID branchId, String title, String body, String kind) {
        try {
            inAppNotificationService.notifySuperAdminsAndBranchManagers(branchId, title, body, kind);
        } catch (RuntimeException ignored) {
            // best-effort; do not block HR operations
        }
    }

    private static String employeeLabel(Employee e) {
        return e.getFirstName() + " " + e.getLastName() + " · " + e.getBranch().getCode();
    }

    /**
     * If this HR record's email matches a dashboard {@link User}, align portal {@link Role} with category:
     * branch coordinator seat (branch.leadTeacher) → CENTRAL_COORDINATOR; other lead teachers → LEAD_TEACHER;
     * MANAGEMENT → SUPER_ADMIN; other categories → staff roles (assistant / cook / cleaner / teacher).
     * Links {@code employee.user} when applicable.
     */
    private void syncPortalAccountFromEmployeeCategory(UUID employeeId) {
        Employee employee = employeeRepository.findWithUserById(employeeId)
                .orElseGet(() -> employeeRepository.findWithBranchById(employeeId)
                        .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found")));
        if (!StringUtils.hasText(employee.getEmail())) {
            return;
        }

        User user = resolveLinkedPortalUser(employee);
        if (user == null) {
            return;
        }

        boolean selectedAsBranchManager = false;
        if (employee.getCategory() == EmployeeCategory.LEAD_TEACHER) {
            selectedAsBranchManager = branchRepository.findWithLeadersById(employee.getBranch().getId())
                    .map(Branch::getLeadTeacher)
                    .map(Employee::getId)
                    .map(id -> id.equals(employee.getId()))
                    .orElse(false);
        }

        Role targetRole;
        if (selectedAsBranchManager) {
            targetRole = Role.CENTRAL_COORDINATOR;
        } else if (employee.getCategory() == EmployeeCategory.MANAGEMENT) {
            targetRole = Role.SUPER_ADMIN;
        } else if (employee.getCategory() == EmployeeCategory.LEAD_TEACHER) {
            targetRole = Role.LEAD_TEACHER;
        } else {
            targetRole = RoleMapper.fromEmployeeCategory(employee.getCategory());
        }

        if (user.getRole() == Role.SUPER_ADMIN && targetRole != Role.SUPER_ADMIN) {
            if (userRepository.countByRole(Role.SUPER_ADMIN) <= 1) {
                throw new BusinessException("error.user.cannot.demote.last.super.admin");
            }
        }

        user.setEmail(employee.getEmail().trim());
        user.setRole(targetRole);
        if (targetRole == Role.SUPER_ADMIN) {
            user.setBranch(null);
        } else {
            user.setBranch(employee.getBranch());
        }
        user.setFirstName(trimToNull(employee.getFirstName()));
        user.setLastName(trimToNull(employee.getLastName()));
        user.setPreferredLanguage(employee.getPreferredLanguage());
        user.setProfilePhotoUrl(trimToNull(employee.getProfilePhotoUrl()));
        userRepository.save(user);

        if (employee.getUser() == null || !employee.getUser().getId().equals(user.getId())) {
            employee.setUser(user);
            employeeRepository.save(employee);
        }
    }

    private User resolveLinkedPortalUser(Employee employee) {
        if (employee.getUser() != null) {
            return userRepository.findWithBranchById(employee.getUser().getId())
                    .orElse(employee.getUser());
        }
        String email = employee.getEmail().trim();
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
        if (byEmail.isPresent()) {
            return byEmail.get();
        }
        if (employee.getBranch() == null) {
            return null;
        }
        return userRepository.findAllActiveWithBranchByBranchId(employee.getBranch().getId()).stream()
                .filter(u -> namesMatch(u, employee))
                .findFirst()
                .orElse(null);
    }

    private static boolean namesMatch(User user, Employee employee) {
        if (!StringUtils.hasText(employee.getFirstName()) || !StringUtils.hasText(employee.getLastName())) {
            return false;
        }
        return employee.getFirstName().trim().equalsIgnoreCase(safeTrim(user.getFirstName()))
                && employee.getLastName().trim().equalsIgnoreCase(safeTrim(user.getLastName()));
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    @Transactional(readOnly = true)
    public PageData<EmployeeResponse> search(
            UserPrincipal principal,
            UUID branchId,
            EmployeeCategory category,
            Boolean managerOnly,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (principal.getRole().isStaffSelfService()) {
            List<Employee> mine = employeeRepository.findAllByUser_Id(principal.getId());
            if (mine.isEmpty()) {
                return PageData.from(Page.empty(pageable));
            }
            Employee self = mine.get(0);
            branchAccessService.assertBranchScope(principal, self.getBranch().getId());
            if (category != null && category != self.getCategory()) {
                return PageData.from(Page.empty(pageable));
            }
            if (Boolean.TRUE.equals(managerOnly)) {
                return PageData.from(Page.empty(pageable));
            }
            Page<Employee> one = new PageImpl<>(List.of(self), pageable, 1);
            return PageData.from(one.map(this::toResponseWithNames));
        }
        UUID scopedBranch = branchId;
        if (principal.getRole().isBranchScopedDashboard()) {
            scopedBranch = principal.getBranchId();
        }
        Page<Employee> result;
        boolean managers = Boolean.TRUE.equals(managerOnly);
        if (scopedBranch != null) {
            branchAccessService.assertBranchScope(principal, scopedBranch);
            if (managers) {
                result = employeeRepository.findBranchManagersByBranch_Id(scopedBranch, pageable);
            } else if (category != null) {
                result = employeeRepository.findByBranch_IdAndCategory(scopedBranch, category, pageable);
            } else {
                result = employeeRepository.findByBranch_Id(scopedBranch, pageable);
            }
        } else {
            branchAccessService.assertOrgWideHrRead(principal);
            if (managers) {
                result = employeeRepository.findBranchManagers(pageable);
            } else if (category != null) {
                result = employeeRepository.findByCategory(category, pageable);
            } else {
                result = employeeRepository.findAllWithBranch(pageable);
            }
        }
        return PageData.from(result.map(this::toResponseWithNames));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getById(UserPrincipal principal, UUID id) {
        Employee employee = employeeRepository.findWithBranchById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
        if (principal.getRole().isStaffSelfService()) {
            if (employee.getUser() == null || !employee.getUser().getId().equals(principal.getId())) {
                throw new AccessDeniedException();
            }
        }
        branchAccessService.assertBranchScope(principal, employee.getBranch().getId());
        return toResponseWithNames(employee);
    }

    private EmployeeResponse toResponseWithNames(Employee e) {
        EmployeeResponse r = employeeMapper.toResponse(e);
        r.setCreatedByName(auditNameResolver.resolveDisplayName(e.getCreatedByEmail()));
        r.setUpdatedByName(auditNameResolver.resolveDisplayName(e.getUpdatedByEmail()));
        return r;
    }

    @Transactional
    public EmployeeResponse create(UserPrincipal principal, CreateEmployeeRequest request) {
        if (principal.getRole() == Role.CENTRAL_COORDINATOR || principal.getRole() == Role.LEAD_TEACHER) {
            branchAccessService.assertBranchManagementWrite(principal, request.getBranchId());
        } else {
            branchAccessService.assertSuperAdminOrGmDelegatedWrite(principal);
        }
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
        assertEmployeeEmailAvailable(request.getEmail(), null);
        String actor = principal.getEmail();
        Employee employee = Employee.builder()
                .branch(branch)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(trimToNull(request.getEmail()))
                .phone(request.getPhone())
                .category(request.getCategory())
                .preferredLanguage(request.getPreferredLanguage())
                .profilePhotoUrl(trimToNull(request.getProfilePhotoUrl()))
                .rfidActive(true)
                .createdByEmail(actor)
                .updatedByEmail(actor)
                .build();
        employee = employeeRepository.save(employee);
        Employee reloaded = employeeRepository.findWithBranchById(employee.getId()).orElseThrow();
        emailService.sendWelcome(reloaded);
        emailService.notifyBranchLeadersNewEmployee(reloaded.getId(), reloaded.getBranch().getId());
        notifyStaffWatchers(reloaded.getBranch().getId(), "New employee", employeeLabel(reloaded), "EMPLOYEE_CREATED");
        syncPortalAccountFromEmployeeCategory(reloaded.getId());
        return toResponseWithNames(employeeRepository.findWithBranchById(reloaded.getId()).orElseThrow());
    }

    @Transactional
    public EmployeeResponse setEmploymentActive(UserPrincipal principal, UUID id, UpdateEmploymentActiveRequest request) {
        Employee employee = employeeRepository.findWithBranchById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
        if (principal.getRole() == Role.CENTRAL_COORDINATOR || principal.getRole() == Role.LEAD_TEACHER) {
            branchAccessService.assertBranchManagementWrite(principal, employee.getBranch().getId());
        } else if (principal.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            branchAccessService.assertSuperAdminOrGmDelegatedWrite(principal);
        } else {
            branchAccessService.assertBranchManagementWrite(principal, employee.getBranch().getId());
        }
        boolean nowActive = Boolean.TRUE.equals(request.getActive());
        employee.setEmploymentActive(nowActive);
        employee.setUpdatedByEmail(principal.getEmail());
        employee = employeeRepository.save(employee);
        Employee out = employeeRepository.findWithBranchById(employee.getId()).orElseThrow();
        notifyStaffWatchers(
                out.getBranch().getId(),
                nowActive ? "Employment restored" : "Employment suspended",
                employeeLabel(out),
                nowActive ? "EMPLOYMENT_RESTORED" : "EMPLOYMENT_SUSPENDED");
        return toResponseWithNames(out);
    }

    @Transactional
    public EmployeeResponse update(UserPrincipal principal, UUID id, UpdateEmployeeRequest request) {
        Employee employee = employeeRepository.findWithBranchById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
        if (principal.getRole() == Role.CENTRAL_COORDINATOR || principal.getRole() == Role.LEAD_TEACHER) {
            branchAccessService.assertBranchManagementWrite(principal, employee.getBranch().getId());
            branchAccessService.assertBranchManagementWrite(principal, request.getBranchId());
        } else {
            branchAccessService.assertSuperAdminOrGmDelegatedWrite(principal);
        }
        assertEmployeeEmailAvailable(request.getEmail(), id);
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
        employee.setBranch(branch);
        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setEmail(trimToNull(request.getEmail()));
        employee.setPhone(request.getPhone());
        employee.setCategory(request.getCategory());
        employee.setPreferredLanguage(request.getPreferredLanguage());
        employee.setProfilePhotoUrl(trimToNull(request.getProfilePhotoUrl()));
        employee.setUpdatedByEmail(principal.getEmail());
        employee = employeeRepository.save(employee);
        Employee out = employeeRepository.findWithBranchById(employee.getId()).orElseThrow();
        notifyStaffWatchers(out.getBranch().getId(), "Employee updated", employeeLabel(out), "EMPLOYEE_UPDATED");
        syncPortalAccountFromEmployeeCategory(out.getId());
        return toResponseWithNames(employeeRepository.findWithBranchById(out.getId()).orElseThrow());
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID id) {
        Employee e = employeeRepository.findWithBranchById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
        if (principal.getRole() == Role.CENTRAL_COORDINATOR || principal.getRole() == Role.LEAD_TEACHER) {
            branchAccessService.assertBranchManagementWrite(principal, e.getBranch().getId());
        } else {
            branchAccessService.assertSuperAdminOrGmDelegatedWrite(principal);
        }
        UUID branchId = e.getBranch().getId();
        String label = e.getFirstName() + " " + e.getLastName() + " · " + e.getBranch().getCode();
        employeeRepository.deleteById(id);
        notifyStaffWatchers(branchId, "Employee removed", label, "EMPLOYEE_DELETED");
    }

    @Transactional
    public RfidStatusResponse assignRfid(UserPrincipal principal, UUID employeeId, AssignRfidRequest request) {
        Employee employee = employeeRepository.findWithBranchById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
        assertEmployeeRfidWrite(principal, employee);
        employeeRepository.findByRfidCardUid(request.getRfidCardUid()).ifPresent(other -> {
            if (!other.getId().equals(employeeId)) {
                throw new BusinessException("error.rfid.uid.taken");
            }
        });
        employee.setRfidCardUid(request.getRfidCardUid());
        employee.setRfidAssignedAt(Instant.now());
        employee.setRfidActive(true);
        employee.setUpdatedByEmail(principal.getEmail());
        employeeRepository.save(employee);
        Employee withBranch = employeeRepository.findWithBranchById(employeeId).orElseThrow();
        emailService.sendRfidAssigned(withBranch);
        notifyStaffWatchers(withBranch.getBranch().getId(), "RFID assigned", employeeLabel(withBranch), "RFID_ASSIGNED");
        return RfidStatusResponse.builder()
                .rfidCardUid(employee.getRfidCardUid())
                .active(employee.isRfidActive())
                .assignedAt(employee.getRfidAssignedAt())
                .build();
    }

    private void assertEmployeeRfidWrite(UserPrincipal principal, Employee employee) {
        if (principal.getRole() == Role.CENTRAL_COORDINATOR || principal.getRole() == Role.LEAD_TEACHER) {
            branchAccessService.assertBranchManagementWrite(principal, employee.getBranch().getId());
            return;
        }
        branchAccessService.assertSuperAdminOrGmDelegatedWrite(principal);
    }

    @Transactional
    public RfidStatusResponse revokeRfid(UserPrincipal principal, UUID employeeId) {
        Employee employee = employeeRepository.findWithBranchById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
        assertEmployeeRfidWrite(principal, employee);
        employee.setRfidActive(false);
        employee.setUpdatedByEmail(principal.getEmail());
        employeeRepository.save(employee);
        Employee withBranch = employeeRepository.findWithBranchById(employeeId).orElseThrow();
        notifyStaffWatchers(withBranch.getBranch().getId(), "RFID revoked", employeeLabel(withBranch), "RFID_REVOKED");
        return RfidStatusResponse.builder()
                .rfidCardUid(employee.getRfidCardUid())
                .active(employee.isRfidActive())
                .assignedAt(employee.getRfidAssignedAt())
                .build();
    }

    @Transactional
    public RfidStatusResponse reactivateRfid(UserPrincipal principal, UUID employeeId) {
        Employee employee = employeeRepository.findWithBranchById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
        assertEmployeeRfidWrite(principal, employee);
        if (employee.getRfidCardUid() == null || employee.getRfidCardUid().isBlank()) {
            throw new BusinessException("error.rfid.reactivate.no.card");
        }
        employee.setRfidActive(true);
        employee.setUpdatedByEmail(principal.getEmail());
        employeeRepository.save(employee);
        Employee withBranch = employeeRepository.findWithBranchById(employeeId).orElseThrow();
        notifyStaffWatchers(withBranch.getBranch().getId(), "RFID reactivated", employeeLabel(withBranch), "RFID_REACTIVATED");
        return RfidStatusResponse.builder()
                .rfidCardUid(employee.getRfidCardUid())
                .active(employee.isRfidActive())
                .assignedAt(employee.getRfidAssignedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public RfidStatusResponse rfidStatus(UserPrincipal principal, UUID employeeId) {
        Employee employee = employeeRepository.findWithBranchById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
        if (principal.getRole().isStaffSelfService()) {
            if (employee.getUser() == null || !employee.getUser().getId().equals(principal.getId())) {
                throw new AccessDeniedException();
            }
        }
        branchAccessService.assertBranchScope(principal, employee.getBranch().getId());
        return RfidStatusResponse.builder()
                .rfidCardUid(employee.getRfidCardUid())
                .active(employee.isRfidActive())
                .assignedAt(employee.getRfidAssignedAt())
                .build();
    }
}
