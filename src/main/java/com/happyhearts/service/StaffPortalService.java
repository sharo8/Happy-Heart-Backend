package com.happyhearts.service;

import com.happyhearts.dto.response.BranchDirectoryEntryResponse;
import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.enums.Role;
import com.happyhearts.exception.AccessDeniedException;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.model.Employee;
import com.happyhearts.model.User;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffPortalService {

    private final StaffScopeService staffScopeService;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<BranchDirectoryEntryResponse> branchDirectory(UserPrincipal principal) {
        if (!staffScopeService.isStaffSelf(principal)) {
            throw new AccessDeniedException();
        }
        UUID branchId = principal.getBranchId();
        if (branchId == null) {
            Employee self = staffScopeService.findLinkedEmployee(principal)
                    .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
            branchId = self.getBranch().getId();
        }

        List<BranchDirectoryEntryResponse> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (User u : userRepository.findAllActiveWithBranchByBranchId(branchId)) {
            if (u.getRole() == Role.CENTRAL_COORDINATOR || u.getRole() == Role.LEAD_TEACHER) {
                String key = "u:" + u.getId();
                if (seen.add(key)) {
                    rows.add(BranchDirectoryEntryResponse.builder()
                            .id(u.getId())
                            .firstName(u.getFirstName())
                            .lastName(u.getLastName())
                            .displayName(displayName(u.getFirstName(), u.getLastName(), u.getEmail()))
                            .portalRole(u.getRole())
                            .roleLabel(u.getRole().name().replace('_', ' '))
                            .sortRank(u.getRole() == Role.CENTRAL_COORDINATOR ? 0 : 1)
                            .build());
                }
            }
        }

        for (Employee e : employeeRepository.findAllByBranch_IdAndEmploymentActiveTrue(branchId)) {
            if (!e.isEmploymentActive()) {
                continue;
            }
            String key = "e:" + e.getId();
            if (!seen.add(key)) {
                continue;
            }
            int rank = 2;
            if (e.getCategory() == EmployeeCategory.LEAD_TEACHER) {
                rank = 1;
            }
            rows.add(BranchDirectoryEntryResponse.builder()
                    .id(e.getId())
                    .firstName(e.getFirstName())
                    .lastName(e.getLastName())
                    .displayName(displayName(e.getFirstName(), e.getLastName(), e.getEmail()))
                    .category(e.getCategory())
                    .roleLabel(e.getCategory() != null ? e.getCategory().name().replace('_', ' ') : "STAFF")
                    .sortRank(rank)
                    .build());
        }

        rows.sort(Comparator
                .comparingInt(BranchDirectoryEntryResponse::getSortRank)
                .thenComparing(r -> r.getDisplayName() != null ? r.getDisplayName() : "", String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private static String displayName(String first, String last, String email) {
        String name = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        return name.isEmpty() ? (email != null ? email : "—") : name;
    }
}
