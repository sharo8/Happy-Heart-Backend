package com.happyhearts.mapper;

import com.happyhearts.dto.response.AttendanceDailyRowResponse;
import com.happyhearts.model.DailyAttendanceSummary;
import com.happyhearts.model.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AttendanceMapper {

    @Mapping(target = "employeeId", source = "employee.id")
    @Mapping(target = "employeeName", expression = "java(employee.getFirstName() + \" \" + employee.getLastName())")
    @Mapping(target = "status", source = "summary.status")
    @Mapping(target = "entryTime", source = "summary.entryTime")
    @Mapping(target = "exitTime", source = "summary.exitTime")
    @Mapping(target = "totalHours", source = "summary.totalHours")
    @Mapping(target = "late", source = "summary.late")
    @Mapping(target = "lateMinutes", source = "summary.lateMinutes")
    AttendanceDailyRowResponse toDailyRow(Employee employee, DailyAttendanceSummary summary);
}
