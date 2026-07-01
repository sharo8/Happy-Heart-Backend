package com.happyhearts.service;

import com.happyhearts.dto.response.AttendanceDailyRowResponse;
import com.happyhearts.dto.response.AttendanceSummaryResponse;
import com.happyhearts.enums.AttendanceStatus;
import com.happyhearts.enums.ExportFormat;
import com.happyhearts.enums.ScanType;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.mapper.AttendanceMapper;
import com.happyhearts.model.Branch;
import com.happyhearts.model.DailyAttendanceSummary;
import com.happyhearts.model.Employee;
import com.happyhearts.repository.AttendanceRecordRepository;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.DailyAttendanceSummaryRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final DailyAttendanceSummaryRepository dailyAttendanceSummaryRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceMapper attendanceMapper;
    private final BranchAccessService branchAccessService;
    private final ReportExportService reportExportService;

    @Transactional(readOnly = true)
    public List<AttendanceDailyRowResponse> daily(UserPrincipal principal, UUID branchId, LocalDate date) {
        branchAccessService.assertBranchScope(principal, branchId);
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
        List<Employee> employees = employeeRepository.findByBranch(branch);
        List<AttendanceDailyRowResponse> rows = new ArrayList<>();
        Instant start = TimeUtils.startOfDayKigali(date);
        Instant end = TimeUtils.endOfDayKigali(date);
        for (Employee employee : employees) {
            Optional<DailyAttendanceSummary> summary =
                    dailyAttendanceSummaryRepository.findByEmployee_IdAndSummaryDate(employee.getId(), date);
            if (summary.isPresent()) {
                rows.add(attendanceMapper.toDailyRow(employee, summary.get()));
            } else {
                boolean hasEntry = attendanceRecordRepository.existsByEmployee_IdAndBranch_IdAndScanTypeAndScannedAtBetween(
                        employee.getId(), branchId, ScanType.ENTRY, start, end);
                if (hasEntry) {
                    rows.add(attendanceMapper.toDailyRow(employee, DailyAttendanceSummary.builder()
                            .status(AttendanceStatus.PRESENT)
                            .late(false)
                            .lateMinutes(0)
                            .build()));
                } else {
                    rows.add(absentRow(employee));
                }
            }
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<AttendanceDailyRowResponse> employeeHistory(
            UserPrincipal principal,
            UUID employeeId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Employee employee = employeeRepository.findWithBranchById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.not.found"));
        branchAccessService.assertBranchScope(principal, employee.getBranch().getId());
        List<DailyAttendanceSummary> summaries = dailyAttendanceSummaryRepository
                .findByEmployee_IdAndSummaryDateBetweenOrderBySummaryDateAsc(employeeId, startDate, endDate);
        List<AttendanceDailyRowResponse> rows = new ArrayList<>();
        for (DailyAttendanceSummary s : summaries) {
            rows.add(attendanceMapper.toDailyRow(employee, s));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryResponse branchSummary(
            UserPrincipal principal,
            UUID branchId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        branchAccessService.assertBranchScope(principal, branchId);
        List<DailyAttendanceSummary> summaries =
                dailyAttendanceSummaryRepository.findByBranchAndDateRange(branchId, startDate, endDate);
        long present = summaries.stream().filter(s -> s.getStatus() == AttendanceStatus.PRESENT).count();
        long absent = summaries.stream().filter(s -> s.getStatus() == AttendanceStatus.ABSENT).count();
        long late = summaries.stream().filter(s -> s.getStatus() == AttendanceStatus.LATE).count();
        BigDecimal avg = summaries.stream()
                .map(DailyAttendanceSummary::getTotalHours)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long cnt = summaries.stream().map(DailyAttendanceSummary::getTotalHours).filter(java.util.Objects::nonNull).count();
        BigDecimal avgHours = cnt == 0
                ? BigDecimal.ZERO
                : avg.divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP);
        return AttendanceSummaryResponse.builder()
                .present(present)
                .absent(absent)
                .late(late)
                .averageHours(avgHours)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AttendanceDailyRowResponse> absences(UserPrincipal principal, UUID branchId, LocalDate date) {
        branchAccessService.assertBranchScope(principal, branchId);
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
        List<Employee> employees = employeeRepository.findByBranch(branch);
        Instant start = TimeUtils.startOfDayKigali(date);
        Instant end = TimeUtils.endOfDayKigali(date);
        List<AttendanceDailyRowResponse> out = new ArrayList<>();
        for (Employee e : employees) {
            Optional<DailyAttendanceSummary> summary =
                    dailyAttendanceSummaryRepository.findByEmployee_IdAndSummaryDate(e.getId(), date);
            if (summary.isPresent()) {
                if (summary.get().getStatus() == AttendanceStatus.ABSENT) {
                    out.add(attendanceMapper.toDailyRow(e, summary.get()));
                }
            } else {
                boolean hasEntry = attendanceRecordRepository.existsByEmployee_IdAndBranch_IdAndScanTypeAndScannedAtBetween(
                        e.getId(), branchId, ScanType.ENTRY, start, end);
                if (!hasEntry) {
                    out.add(absentRow(e));
                }
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public byte[] export(
            UserPrincipal principal,
            UUID branchId,
            LocalDate startDate,
            LocalDate endDate,
            ExportFormat format
    ) {
        branchAccessService.assertBranchScope(principal, branchId);
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
        List<DailyAttendanceSummary> summaries =
                dailyAttendanceSummaryRepository.findByBranchAndDateRange(branchId, startDate, endDate);
        String generatedBy = (principal.getFirstName() + " " + principal.getLastName()).trim();
        if (generatedBy.isBlank()) {
            generatedBy = principal.getEmail();
        }
        return reportExportService.export(branch, summaries, startDate, endDate, generatedBy, format);
    }

    private AttendanceDailyRowResponse absentRow(Employee employee) {
        return AttendanceDailyRowResponse.builder()
                .employeeId(employee.getId())
                .employeeName(employee.getFirstName() + " " + employee.getLastName())
                .status(AttendanceStatus.ABSENT)
                .late(false)
                .lateMinutes(0)
                .build();
    }
}
