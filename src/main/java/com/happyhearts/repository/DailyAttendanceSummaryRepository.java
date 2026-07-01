package com.happyhearts.repository;

import com.happyhearts.model.DailyAttendanceSummary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyAttendanceSummaryRepository extends JpaRepository<DailyAttendanceSummary, UUID> {

    List<DailyAttendanceSummary> findByBranch_IdAndSummaryDate(UUID branchId, LocalDate date);

    List<DailyAttendanceSummary> findByEmployee_IdAndSummaryDateBetweenOrderBySummaryDateAsc(
            UUID employeeId, LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = "employee")
    @Query("""
            SELECT s FROM DailyAttendanceSummary s
            WHERE s.branch.id = :branchId
            AND s.summaryDate BETWEEN :start AND :end
            """)
    List<DailyAttendanceSummary> findByBranchAndDateRange(
            @Param("branchId") UUID branchId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    Optional<DailyAttendanceSummary> findByEmployee_IdAndSummaryDate(UUID employeeId, LocalDate date);
}
