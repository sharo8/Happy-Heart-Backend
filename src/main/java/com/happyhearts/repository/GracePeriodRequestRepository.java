package com.happyhearts.repository;

import com.happyhearts.enums.GracePeriodStatus;
import com.happyhearts.model.GracePeriodRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GracePeriodRequestRepository extends JpaRepository<GracePeriodRequest, UUID> {

    @Query("""
            SELECT g FROM GracePeriodRequest g
            JOIN FETCH g.employee e
            LEFT JOIN FETCH e.branch
            WHERE (:employeeId IS NULL OR e.id = :employeeId)
              AND (:branchId IS NULL OR e.branch.id = :branchId)
              AND (:status IS NULL OR g.status = :status)
              AND g.dateTo >= :from AND g.dateFrom <= :to
            ORDER BY g.createdAt DESC
            """)
    List<GracePeriodRequest> search(
            @Param("employeeId") UUID employeeId,
            @Param("branchId") UUID branchId,
            @Param("status") GracePeriodStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Query("""
            SELECT COUNT(g) > 0 FROM GracePeriodRequest g
            WHERE g.employee.id = :employeeId
              AND g.status = com.happyhearts.enums.GracePeriodStatus.APPROVED
              AND g.source = com.happyhearts.enums.GracePeriodSource.ATTENDANCE
              AND g.dateFrom <= :day AND g.dateTo >= :day
            """)
    boolean hasApprovedGrace(@Param("employeeId") UUID employeeId, @Param("day") LocalDate day);
}
