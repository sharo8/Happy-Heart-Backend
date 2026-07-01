package com.happyhearts.service;

import com.happyhearts.exception.AccessDeniedException;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.model.Employee;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffScopeService {

    private final EmployeeRepository employeeRepository;

    public boolean isStaffSelf(UserPrincipal principal) {
        return principal != null && principal.getRole() != null && principal.getRole().isStaffSelfService();
    }

    public Optional<Employee> findLinkedEmployee(UserPrincipal principal) {
        if (principal == null) {
            return Optional.empty();
        }
        List<Employee> byUser = employeeRepository.findAllByUser_Id(principal.getId());
        if (!byUser.isEmpty()) {
            return Optional.of(byUser.get(0));
        }
        if (StringUtils.hasText(principal.getEmail())) {
            return employeeRepository.findAll().stream()
                    .filter(e -> e.getEmail() != null
                            && e.getEmail().trim().equalsIgnoreCase(principal.getEmail().trim()))
                    .findFirst();
        }
        return Optional.empty();
    }

    public UUID requireOwnEmployeeId(UserPrincipal principal) {
        return findLinkedEmployee(principal)
                .map(Employee::getId)
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
    }

    public UUID resolveEmployeeFilter(UserPrincipal principal, UUID requestedEmployeeId) {
        if (!isStaffSelf(principal)) {
            return requestedEmployeeId;
        }
        return requireOwnEmployeeId(principal);
    }

    public void assertOwnEmployee(UserPrincipal principal, UUID employeeId) {
        if (!isStaffSelf(principal)) {
            return;
        }
        if (!requireOwnEmployeeId(principal).equals(employeeId)) {
            throw new AccessDeniedException();
        }
    }
}
