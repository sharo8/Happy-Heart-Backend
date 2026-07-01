package com.happyhearts.repository;

import com.happyhearts.model.AttendanceExcuse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AttendanceExcuseRepository extends JpaRepository<AttendanceExcuse, UUID> {

    @Query("""
            SELECT x FROM AttendanceExcuse x
            JOIN FETCH x.employee e
            LEFT JOIN FETCH e.branch
            JOIN FETCH x.grantedBy
            WHERE (:employeeId IS NULL OR e.id = :employeeId)
              AND (:branchId IS NULL OR e.branch.id = :branchId)
              AND x.dateTo >= :from AND x.dateFrom <= :to
            ORDER BY x.createdAt DESC
            """)
    List<AttendanceExcuse> search(
            @Param("employeeId") UUID employeeId,
            @Param("branchId") UUID branchId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Query("""
            SELECT COUNT(x) > 0 FROM AttendanceExcuse x
            WHERE x.employee.id = :employeeId
              AND x.dateFrom <= :day AND x.dateTo >= :day
            """)
    boolean hasExcuse(@Param("employeeId") UUID employeeId, @Param("day") LocalDate day);
}
