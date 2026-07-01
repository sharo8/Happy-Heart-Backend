package com.happyhearts.service;

import com.happyhearts.dto.response.AttendanceSummaryResponse;
import com.happyhearts.enums.AttendanceStatus;
import com.happyhearts.enums.Language;
import com.happyhearts.enums.Role;
import com.happyhearts.mapper.AttendanceMapper;
import com.happyhearts.model.Branch;
import com.happyhearts.model.DailyAttendanceSummary;
import com.happyhearts.model.Employee;
import com.happyhearts.model.User;
import com.happyhearts.repository.AttendanceRecordRepository;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.DailyAttendanceSummaryRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private DailyAttendanceSummaryRepository dailyAttendanceSummaryRepository;
    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;
    @Spy
    private AttendanceMapper attendanceMapper = Mappers.getMapper(AttendanceMapper.class);
    @Mock
    private BranchAccessService branchAccessService;
    @Mock
    private ReportExportService reportExportService;

    @InjectMocks
    private AttendanceService attendanceService;

    @Test
    void branchSummary_aggregatesStatuses() {
        UUID branchId = UUID.randomUUID();
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("m@x.rw")
                .password("x")
                .role(Role.SUPER_ADMIN)
                .preferredLanguage(Language.EN)
                .active(true)
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        Branch branch = Branch.builder().id(branchId).build();
        Employee employee = Employee.builder()
                .id(UUID.randomUUID())
                .firstName("N")
                .lastName("M")
                .branch(branch)
                .build();
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 30);
        List<DailyAttendanceSummary> summaries = List.of(
                DailyAttendanceSummary.builder()
                        .employee(employee)
                        .branch(branch)
                        .summaryDate(start)
                        .status(AttendanceStatus.PRESENT)
                        .totalHours(BigDecimal.valueOf(8))
                        .build(),
                DailyAttendanceSummary.builder()
                        .employee(employee)
                        .branch(branch)
                        .summaryDate(start.plusDays(1))
                        .status(AttendanceStatus.ABSENT)
                        .build(),
                DailyAttendanceSummary.builder()
                        .employee(employee)
                        .branch(branch)
                        .summaryDate(start.plusDays(2))
                        .status(AttendanceStatus.LATE)
                        .totalHours(BigDecimal.valueOf(7))
                        .build()
        );
        doNothing().when(branchAccessService).assertBranchScope(any(), any());
        when(dailyAttendanceSummaryRepository.findByBranchAndDateRange(branchId, start, end)).thenReturn(summaries);

        AttendanceSummaryResponse res = attendanceService.branchSummary(principal, branchId, start, end);
        assertEquals(1, res.getPresent());
        assertEquals(1, res.getAbsent());
        assertEquals(1, res.getLate());
        assertEquals(0, new BigDecimal("7.50").compareTo(res.getAverageHours()));
    }
}
