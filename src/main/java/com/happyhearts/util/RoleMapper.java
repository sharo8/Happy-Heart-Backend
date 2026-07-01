package com.happyhearts.util;

import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.enums.Role;

public final class RoleMapper {

    private RoleMapper() {
    }

    /** Maps HR {@link EmployeeCategory} to portal {@link Role} when the user is not the branch coordinator seat. */
    public static Role fromEmployeeCategory(EmployeeCategory category) {
        if (category == null) {
            return Role.TEACHER;
        }
        return switch (category) {
            case LEAD_TEACHER -> Role.LEAD_TEACHER;
            case TEACHER_ASSISTANT -> Role.ASSISTANT;
            case CLEANER -> Role.CLEANER;
            case COOK -> Role.COOK;
            default -> Role.TEACHER;
        };
    }
}
