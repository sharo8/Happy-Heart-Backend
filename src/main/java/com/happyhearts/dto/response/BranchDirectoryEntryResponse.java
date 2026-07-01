package com.happyhearts.dto.response;

import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class BranchDirectoryEntryResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String displayName;
    private EmployeeCategory category;
    private Role portalRole;
    private String roleLabel;
    private int sortRank;
}
