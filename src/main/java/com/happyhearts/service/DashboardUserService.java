package com.happyhearts.service;

import com.happyhearts.config.AuthOtpProperties;
import com.happyhearts.dto.request.CreateDashboardUserRequest;
import com.happyhearts.dto.request.UpdateDashboardUserRequest;
import com.happyhearts.dto.request.UpdateUserActiveRequest;
import com.happyhearts.dto.response.BranchManagerLookupResponse;
import com.happyhearts.dto.response.CreatedUserResponse;
import com.happyhearts.dto.response.UserListResponse;
import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.enums.Role;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Employee;
import com.happyhearts.model.User;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.InAppNotificationRepository;
import com.happyhearts.repository.LoginOtpChallengeRepository;
import com.happyhearts.repository.PasswordResetTokenRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.util.RoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardUserService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final EmployeeRepository employeeRepository;
    private final InAppNotificationRepository inAppNotificationRepository;
    private final LoginOtpChallengeRepository loginOtpChallengeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final BranchAccessService branchAccessService;
    private final PasswordEncoder passwordEncoder;
    private final AuthOtpProperties authOtpProperties;
    private final EmailService emailService;
    private final InAppNotificationService inAppNotificationService;
    private final AuditService auditService;
    private final GmPermissionService gmPermissionService;

    /**
     * Central coordinator user for a branch (for org chart in employee preview). Scoped to callers who may read the branch.
     */
    @Transactional(readOnly = true)
    public BranchManagerLookupResponse findBranchManagerForBranch(UserPrincipal principal, UUID branchId) {
        branchAccessService.assertBranchScope(principal, branchId);
        List<User> managers = userRepository.findByBranch_IdAndRole(branchId, Role.CENTRAL_COORDINATOR);
        Optional<User> active = managers.stream().filter(User::isActive).findFirst();
        User u = active.orElseGet(() -> managers.isEmpty() ? null : managers.get(0));
        if (u == null) {
            return null;
        }
        return BranchManagerLookupResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .role(u.getRole())
                .branchId(branchId)
                .branchCode(u.getBranch() != null ? u.getBranch().getCode() : null)
                .build();
    }

    @Transactional
    public List<UserListResponse> listDashboardUsers(UserPrincipal admin) {
        branchAccessService.assertOrgWideHrRead(admin);
        reconcileAllBranchManagerPortalRoles();
        List<User> users = userRepository.findAllWithBranch(Sort.by(Sort.Direction.ASC, "email"));
        return users.stream()
                .map(this::reconcileUserFromLinkedEmployeeSafely)
                .map(this::toListResponse)
                .toList();
    }

    private User reconcileUserFromLinkedEmployeeSafely(User user) {
        try {
            return reconcileUserFromLinkedEmployee(user);
        } catch (RuntimeException ex) {
            log.warn("Could not reconcile portal user {} ({}): {}", user.getId(), user.getEmail(), ex.getMessage());
            return user;
        }
    }

    @Transactional
    public UserListResponse setUserActive(UserPrincipal admin, UUID userId, UpdateUserActiveRequest request) {
        branchAccessService.assertSuperAdminOrGmDelegatedWrite(admin);
        boolean active = Boolean.TRUE.equals(request.getActive());
        if (!active && admin.getId().equals(userId)) {
            throw new BusinessException("error.user.cannot.deactivate.self");
        }
        User user = userRepository.findWithBranchById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
        user.setActive(active);
        userRepository.save(user);
        auditService.log(admin, active ? "USER_ACTIVATED" : "USER_DEACTIVATED", userId, "user", null, null);
        User reloaded = userRepository.findWithBranchById(userId).orElseThrow();
        return toListResponse(reloaded);
    }

    private UserListResponse toListResponse(User u) {
        return UserListResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .role(u.getRole())
                .preferredLanguage(u.getPreferredLanguage())
                .active(u.isActive())
                .branchId(u.getBranch() != null ? u.getBranch().getId() : null)
                .branchCode(u.getBranch() != null ? u.getBranch().getCode() : null)
                .profilePhotoUrl(u.getProfilePhotoUrl())
                .build();
    }

    @Transactional
    public CreatedUserResponse createDashboardUser(UserPrincipal admin, CreateDashboardUserRequest request) {
        branchAccessService.assertSuperAdminOrGmDelegatedWrite(admin);
        assertGmpCannotAssignSuperAdmin(admin, request.getRole());
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessException("error.user.email.exists");
        }
        if (employeeRepository.existsByNormalizedEmail(normalizedEmail, null)) {
            throw new BusinessException("error.user.email.used.by.employee");
        }
        if (!request.getRole().isAssignableFromAdminUserForm()) {
            throw new BusinessException("error.user.dashboard.role");
        }
        if (request.getRole() == Role.CENTRAL_COORDINATOR) {
            if (request.getBranchId() == null) {
                throw new BusinessException("error.user.branch.required");
            }
            assertCentralCoordinatorSeatOnBranch(null, request.getEmail(), request.getBranchId(), null);
        } else if (request.getRole() == Role.LEAD_TEACHER) {
            if (request.getBranchId() == null) {
                throw new BusinessException("error.user.branch.required");
            }
        } else if ((request.getRole() == Role.SUPER_ADMIN || request.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE)
                && request.getBranchId() != null) {
            throw new BusinessException("error.user.branch.must.be.null");
        }

        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
        }

        byte[] randomPwd = new byte[24];
        new SecureRandom().nextBytes(randomPwd);
        String internalPassword = passwordEncoder.encode(Base64.getEncoder().encodeToString(randomPwd));

        String setupToken = generateSetupToken();
        Instant setupExpiry = Instant.now().plus(authOtpProperties.getSetupTokenExpirationHours(), ChronoUnit.HOURS);

        User user = User.builder()
                .email(request.getEmail().trim().toLowerCase())
                .firstName(trimToNull(request.getFirstName()))
                .lastName(trimToNull(request.getLastName()))
                .password(internalPassword)
                .role(request.getRole())
                .preferredLanguage(request.getPreferredLanguage())
                .branch(branch)
                .active(true)
                .passwordChangeRequired(false)
                .initialSetupToken(setupToken)
                .initialSetupTokenExpiresAt(setupExpiry)
                .build();
        user = userRepository.save(user);
        if (user.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            gmPermissionService.ensureDefaultsForUser(user.getId());
        }
        auditService.log(admin, "USER_CREATED", user.getId(), "user", null, branch != null ? branch.getId() : null);

        try {
            UUID scopeBranch = branch != null ? branch.getId() : null;
            String roleLabel = user.getRole().name().replace('_', ' ');
            inAppNotificationService.notifySuperAdminsAndBranchManagers(
                    scopeBranch,
                    "New dashboard user",
                    user.getEmail() + " · " + roleLabel,
                    "DASHBOARD_USER_CREATED");
        } catch (Exception ex) {
            log.warn("Could not notify admins about new dashboard user: {}", ex.getMessage());
        }

        boolean sent = false;
        try {
            emailService.sendWelcomeAndPasswordSetup(user, setupToken);
            sent = true;
        } catch (Exception e) {
            log.warn("Onboarding email could not be sent to {}: {}", user.getEmail(), e.getMessage());
        }

        return CreatedUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .preferredLanguage(user.getPreferredLanguage())
                .branchId(branch != null ? branch.getId() : null)
                .onboardingEmailSent(sent)
                .build();
    }

    @Transactional
    public UserListResponse updateDashboardUser(UserPrincipal admin, UUID userId, UpdateDashboardUserRequest request) {
        branchAccessService.assertSuperAdminOrGmDelegatedWrite(admin);
        User user = userRepository.findWithBranchById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
        assertGmpCannotModifySuperAdminUser(admin, user);
        Role newRole = request.getRole();
        assertGmpCannotAssignSuperAdmin(admin, newRole);
        long saCount = userRepository.countByRole(Role.SUPER_ADMIN);
        if (user.getRole() == Role.SUPER_ADMIN && newRole != Role.SUPER_ADMIN && saCount <= 1) {
            throw new BusinessException("error.user.cannot.demote.last.super.admin");
        }

        assertUserEmailAvailable(request.getEmail(), userId);

        if (newRole == Role.CENTRAL_COORDINATOR) {
            if (request.getBranchId() == null) {
                throw new BusinessException("error.user.branch.required");
            }
            assertCentralCoordinatorSeatOnBranch(userId, request.getEmail(), request.getBranchId(), user);
            Branch branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
            user.setBranch(branch);
        } else if (newRole == Role.LEAD_TEACHER) {
            if (request.getBranchId() == null) {
                throw new BusinessException("error.user.branch.required");
            }
            Branch branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
            user.setBranch(branch);
        } else if (newRole == Role.SUPER_ADMIN || newRole == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            if (request.getBranchId() != null) {
                throw new BusinessException("error.user.branch.must.be.null");
            }
            user.setBranch(null);
        } else if (newRole.requiresBranchAssignment()) {
            if (request.getBranchId() == null) {
                throw new BusinessException("error.user.branch.required");
            }
            Branch branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
            user.setBranch(branch);
        } else {
            throw new BusinessException("error.user.dashboard.role");
        }

        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setRole(newRole);
        user.setFirstName(trimToNull(request.getFirstName()));
        user.setLastName(trimToNull(request.getLastName()));
        user.setPreferredLanguage(request.getPreferredLanguage());
        user.setProfilePhotoUrl(trimToNull(request.getProfilePhotoUrl()));

        userRepository.save(user);
        syncLinkedEmployeesFromUser(user, request.getPhone());
        auditService.log(admin, "USER_UPDATED", userId, "user", "{\"role\":\"" + newRole.name() + "\"}", null);
        User reloaded = userRepository.findWithBranchById(userId).orElseThrow();
        return toListResponse(reloaded);
    }

    @Transactional
    public void deleteDashboardUser(UserPrincipal admin, UUID userId) {
        branchAccessService.assertSuperAdminOrGmDelegatedWrite(admin);
        if (admin.getId().equals(userId)) {
            throw new BusinessException("error.user.cannot.delete.self");
        }
        User user = userRepository.findWithBranchById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found"));
        assertGmpCannotModifySuperAdminUser(admin, user);
        long saCount = userRepository.countByRole(Role.SUPER_ADMIN);
        if (user.getRole() == Role.SUPER_ADMIN && saCount <= 1) {
            throw new BusinessException("error.user.cannot.delete.last.super.admin");
        }

        for (Employee e : employeeRepository.findAllByUser_Id(userId)) {
            e.setUser(null);
            employeeRepository.save(e);
        }

        passwordResetTokenRepository.deleteAllByUser_Id(userId);
        loginOtpChallengeRepository.deleteAllByUser_Id(userId);
        inAppNotificationRepository.deleteAllByUser_Id(userId);

        auditService.log(admin, "USER_DELETED", userId, "user", null, null);
        userRepository.delete(user);
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private void assertUserEmailAvailable(String email, UUID userId) {
        String normalized = email.trim().toLowerCase();
        userRepository.findByEmailIgnoreCase(normalized).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) {
                throw new BusinessException("error.user.email.exists");
            }
        });
        employeeRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(normalized).ifPresent(employee -> {
            boolean owned = employeeRepository.findAllByUser_Id(userId).stream()
                    .anyMatch(linked -> linked.getId().equals(employee.getId()));
            if (!owned) {
                throw new BusinessException("error.user.email.used.by.employee");
            }
        });
    }

    private void syncLinkedEmployeesFromUser(User user, String phone) {
        List<Employee> linked = employeeRepository.findAllByUser_Id(user.getId());
        for (Employee employee : linked) {
            employee.setEmail(user.getEmail());
            employee.setFirstName(user.getFirstName());
            employee.setLastName(user.getLastName());
            employee.setPreferredLanguage(user.getPreferredLanguage());
            employee.setProfilePhotoUrl(user.getProfilePhotoUrl());
            employee.setPhone(trimToNull(phone));
            if (user.getBranch() != null) {
                employee.setBranch(user.getBranch());
            }
            employeeRepository.save(employee);
        }
    }

    /**
     * When HR and portal records are linked but contact fields diverged (e.g. email changed on employee only),
     * align the portal user from the linked employee record.
     */
    private User reconcileUserFromLinkedEmployee(User user) {
        List<Employee> linked = employeeRepository.findAllWithBranchByUser_Id(user.getId());
        if (linked.isEmpty()) {
            linked = findEmployeesForPortalUser(user);
            if (linked.size() == 1) {
                Employee employee = employeeRepository.findWithBranchById(linked.get(0).getId()).orElse(linked.get(0));
                employee.setUser(user);
                employeeRepository.save(employee);
                linked = List.of(employee);
            } else {
                return user;
            }
        }
        Employee employee = linked.stream()
                .max(Comparator.comparing(
                        e -> e.getUpdatedAt() != null ? e.getUpdatedAt() : e.getCreatedAt(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(linked.get(0));
        employee = employeeRepository.findWithBranchById(employee.getId()).orElse(employee);
        String employeeEmail = trimToNull(employee.getEmail());
        if (employeeEmail == null) {
            return user;
        }
        boolean changed = false;
        if (!employeeEmail.equalsIgnoreCase(user.getEmail())) {
            Optional<User> conflict = userRepository.findByEmailIgnoreCase(employeeEmail);
            if (conflict.isPresent() && !conflict.get().getId().equals(user.getId())) {
                return user;
            }
            user.setEmail(employeeEmail.toLowerCase());
            changed = true;
        }
        if (!Objects.equals(trimToNull(employee.getFirstName()), user.getFirstName())) {
            user.setFirstName(trimToNull(employee.getFirstName()));
            changed = true;
        }
        if (!Objects.equals(trimToNull(employee.getLastName()), user.getLastName())) {
            user.setLastName(trimToNull(employee.getLastName()));
            changed = true;
        }
        if (!Objects.equals(trimToNull(employee.getProfilePhotoUrl()), user.getProfilePhotoUrl())) {
            user.setProfilePhotoUrl(trimToNull(employee.getProfilePhotoUrl()));
            changed = true;
        }
        if (employee.getPreferredLanguage() != null && employee.getPreferredLanguage() != user.getPreferredLanguage()) {
            user.setPreferredLanguage(employee.getPreferredLanguage());
            changed = true;
        }
        if (employee.getBranch() != null
                && (user.getBranch() == null || !employee.getBranch().getId().equals(user.getBranch().getId()))) {
            user.setBranch(employee.getBranch());
            changed = true;
        }
        Role targetRole = resolvePortalRoleForEmployee(employee);
        if (user.getRole() != targetRole
                && !(user.getRole() == Role.SUPER_ADMIN && targetRole != Role.SUPER_ADMIN)) {
            user.setRole(targetRole);
            changed = true;
        }
        if (changed) {
            user = userRepository.save(user);
            return userRepository.findWithBranchById(user.getId()).orElse(user);
        }
        return user;
    }

    /** Align portal roles for every branch coordinator seat (branch.leadTeacher). */
    private void reconcileAllBranchManagerPortalRoles() {
        for (Branch branch : branchRepository.findAllWithLeaders()) {
            try {
                reconcileBranchManagerPortalRole(branch);
            } catch (RuntimeException ex) {
                log.warn("Could not reconcile branch manager for {}: {}", branch.getCode(), ex.getMessage());
            }
        }
    }

    private void reconcileBranchManagerPortalRole(Branch branch) {
        Employee manager = branch.getLeadTeacher();
        if (manager == null) {
            return;
        }
        Employee employee = employeeRepository.findWithUserById(manager.getId())
                .orElseGet(() -> employeeRepository.findWithBranchById(manager.getId()).orElse(null));
        if (employee == null) {
            return;
        }
        User user = resolvePortalUserForEmployee(employee);
        if (user == null) {
            return;
        }
        user = userRepository.findWithBranchById(user.getId()).orElse(user);
        user.setRole(Role.CENTRAL_COORDINATOR);
        user.setBranch(branch);
        applyEmployeeEmailIfAvailable(user, employee.getEmail());
        user.setFirstName(trimToNull(employee.getFirstName()));
        user.setLastName(trimToNull(employee.getLastName()));
        if (employee.getPreferredLanguage() != null) {
            user.setPreferredLanguage(employee.getPreferredLanguage());
        }
        user.setProfilePhotoUrl(trimToNull(employee.getProfilePhotoUrl()));
        userRepository.save(user);

        Employee linked = employeeRepository.findWithUserById(employee.getId()).orElse(employee);
        if (linked.getUser() == null || !linked.getUser().getId().equals(user.getId())) {
            linked.setUser(user);
            employeeRepository.save(linked);
        }
    }

    private void applyEmployeeEmailIfAvailable(User user, String email) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        String normalized = email.trim().toLowerCase();
        Optional<User> owner = userRepository.findByEmailIgnoreCase(normalized);
        if (owner.isPresent() && !owner.get().getId().equals(user.getId())) {
            return;
        }
        user.setEmail(normalized);
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

    private List<Employee> findEmployeesForPortalUser(User user) {
        if (user.getBranch() == null || !StringUtils.hasText(user.getFirstName()) || !StringUtils.hasText(user.getLastName())) {
            return List.of();
        }
        return employeeRepository.findAllByBranch_Id(user.getBranch().getId()).stream()
                .filter(e -> namesMatch(user, e))
                .filter(e -> e.getUser() == null || e.getUser().getId().equals(user.getId()))
                .toList();
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

    private void assertGmpCannotAssignSuperAdmin(UserPrincipal admin, Role targetRole) {
        if (admin.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE && targetRole == Role.SUPER_ADMIN) {
            throw new BusinessException("error.user.gmp.cannot.assign.superadmin");
        }
    }

    private void assertGmpCannotModifySuperAdminUser(UserPrincipal admin, User target) {
        if (admin.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE && target.getRole() == Role.SUPER_ADMIN) {
            throw new BusinessException("error.user.gmp.cannot.modify.superadmin");
        }
    }

    private Role resolvePortalRoleForEmployee(Employee employee) {
        if (employee.getCategory() == EmployeeCategory.MANAGEMENT) {
            return Role.SUPER_ADMIN;
        }
        UUID employeeId = employee.getId();
        UUID branchId = employee.getBranch() != null ? employee.getBranch().getId() : null;
        if (branchId != null) {
            boolean isBranchManager = branchRepository.findWithLeadersById(branchId)
                    .map(Branch::getLeadTeacher)
                    .map(Employee::getId)
                    .map(id -> id.equals(employeeId))
                    .orElse(false);
            if (isBranchManager) {
                return Role.CENTRAL_COORDINATOR;
            }
        }
        if (employee.getCategory() == EmployeeCategory.LEAD_TEACHER) {
            return Role.LEAD_TEACHER;
        }
        return RoleMapper.fromEmployeeCategory(employee.getCategory());
    }

    private void assertCentralCoordinatorSeatOnBranch(UUID userId, String email, UUID branchId, User user) {
        Employee employee = resolveEmployeeForPortalUser(userId, email, user);
        if (employee == null) {
            throw new BusinessException("error.user.branch.manager.requires.lead.teacher");
        }
        if (employee.getCategory() != EmployeeCategory.LEAD_TEACHER) {
            throw new BusinessException("error.user.branch.manager.requires.lead.teacher");
        }
        if (employee.getBranch() == null || !employee.getBranch().getId().equals(branchId)) {
            throw new BusinessException("error.user.branch.manager.requires.lead.teacher.same.branch");
        }
    }

    private Employee resolveEmployeeForPortalUser(UUID userId, String email, User user) {
        if (userId != null) {
            List<Employee> linked = employeeRepository.findAllByUser_Id(userId);
            if (!linked.isEmpty()) {
                Employee picked = linked.stream()
                        .max(Comparator.comparing(
                                e -> e.getUpdatedAt() != null ? e.getUpdatedAt() : e.getCreatedAt(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(linked.get(0));
                return employeeRepository.findWithBranchById(picked.getId()).orElse(picked);
            }
        }
        if (StringUtils.hasText(email)) {
            Optional<Employee> byEmail = employeeRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email.trim());
            if (byEmail.isPresent()) {
                return employeeRepository.findWithBranchById(byEmail.get().getId()).orElse(byEmail.get());
            }
        }
        if (user != null) {
            List<Employee> candidates = findEmployeesForPortalUser(user);
            if (candidates.size() == 1) {
                return employeeRepository.findWithBranchById(candidates.get(0).getId()).orElse(candidates.get(0));
            }
        }
        return null;
    }

    private static String generateSetupToken() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
