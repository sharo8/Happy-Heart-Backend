package com.happyhearts.repository;

import com.happyhearts.enums.ScanType;
import com.happyhearts.model.AttendanceRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    List<AttendanceRecord> findByEmployee_IdAndScannedAtBetweenOrderByScannedAtAsc(
            UUID employeeId, Instant start, Instant end);

    List<AttendanceRecord> findByBranch_IdAndScannedAtBetweenOrderByScannedAtAsc(
            UUID branchId, Instant start, Instant end);

    @Query("""
            SELECT ar FROM AttendanceRecord ar
            WHERE ar.rfidCardUid = :uid
            AND ar.scanType = :scanType
            AND ar.scannedAt >= :since
            ORDER BY ar.scannedAt DESC
            """)
    List<AttendanceRecord> findRecentByCardAndType(
            @Param("uid") String uid,
            @Param("scanType") ScanType scanType,
            @Param("since") Instant since);

    @Query("""
            SELECT ar FROM AttendanceRecord ar
            WHERE ar.rfidCardUid = :uid
            AND ar.scanType = :scanType
            AND ar.scannedAt >= :minuteStart
            AND ar.scannedAt < :minuteEnd
            """)
    Optional<AttendanceRecord> findDuplicateInMinuteWindow(
            @Param("uid") String uid,
            @Param("scanType") ScanType scanType,
            @Param("minuteStart") Instant minuteStart,
            @Param("minuteEnd") Instant minuteEnd);

    boolean existsByEmployee_IdAndBranch_IdAndScanTypeAndScannedAtBetween(
            UUID employeeId, UUID branchId, ScanType scanType, Instant start, Instant end);

    @Query("""
            SELECT ar FROM AttendanceRecord ar
            WHERE ar.employee.id = :employeeId
            AND ar.scannedAt >= :dayStart
            AND ar.scannedAt < :dayEnd
            ORDER BY ar.scannedAt DESC
            """)
    List<AttendanceRecord> findTodayByEmployee(
            @Param("employeeId") UUID employeeId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd);

    boolean existsByRfidCardUidAndScannedAt(String rfidCardUid, Instant scannedAt);

    @Query("""
            SELECT ar FROM AttendanceRecord ar
            JOIN FETCH ar.employee e
            LEFT JOIN FETCH ar.rfidReader r
            WHERE ar.scannedAt >= :dayStart AND ar.scannedAt < :dayEnd
            ORDER BY ar.scannedAt DESC
            """)
    List<AttendanceRecord> findRecentForDay(
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd,
            Pageable pageable);

    @Query("""
            SELECT ar FROM AttendanceRecord ar
            JOIN FETCH ar.employee e
            LEFT JOIN FETCH e.branch
            WHERE ar.scannedAt >= :start AND ar.scannedAt < :end
            """)
    List<AttendanceRecord> findInRangeWithEmployee(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            SELECT ar FROM AttendanceRecord ar
            JOIN FETCH ar.employee e
            LEFT JOIN FETCH e.branch
            WHERE ar.scannedAt >= :start AND ar.scannedAt < :end
              AND e.branch.id = :branchId
            """)
    List<AttendanceRecord> findInRangeForBranchWithEmployee(
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("branchId") UUID branchId);
}
