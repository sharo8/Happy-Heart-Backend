package com.happyhearts.mapper;

import com.happyhearts.dto.response.BranchResponse;
import com.happyhearts.dto.response.EmployeeResponse;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EmployeeMapper {

    @Mapping(target = "branchId", source = "branch.id")
    @Mapping(target = "branchCode", source = "branch.code")
    @Mapping(target = "portalUserId", source = "user.id")
    EmployeeResponse toResponse(Employee employee);

    BranchResponse branchToResponse(Branch branch);
}
