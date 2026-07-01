package com.happyhearts.service;

import com.happyhearts.model.Employee;
import com.happyhearts.model.User;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Resolves a display name for audit emails: prefer {@link User} first/last name,
 * then an {@link Employee} row with the same email.
 */
@Service
@RequiredArgsConstructor
public class AuditNameResolver {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public String resolveDisplayName(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        String e = email.trim();
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(e);
        if (userOpt.isPresent()) {
            User u = userOpt.get();
            String combined = (StringUtils.hasText(u.getFirstName()) ? u.getFirstName().trim() : "")
                    + " "
                    + (StringUtils.hasText(u.getLastName()) ? u.getLastName().trim() : "");
            combined = combined.trim();
            if (StringUtils.hasText(combined)) {
                return combined;
            }
            if (StringUtils.hasText(u.getEmail())) {
                return u.getEmail().trim();
            }
        }
        Optional<String> fromEmployee = employeeRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(e)
                .map(emp -> (emp.getFirstName() + " " + emp.getLastName()).trim())
                .filter(StringUtils::hasText);
        if (fromEmployee.isPresent()) {
            return fromEmployee.get();
        }
        return e;
    }
}
