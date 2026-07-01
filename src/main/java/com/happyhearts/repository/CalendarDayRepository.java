package com.happyhearts.repository;

import com.happyhearts.model.CalendarDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CalendarDayRepository extends JpaRepository<CalendarDay, UUID> {

    @Query("""
            select cd from CalendarDay cd
            join fetch cd.calendarEvent e
            where cd.dayDate >= :from and cd.dayDate <= :to
              and (e.applyToAllBranches = true or e.branch.id = :branchId)
            order by cd.dayDate asc, e.startDate asc, e.id asc
            """)
    List<CalendarDay> findMaterializedInRange(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("branchId") UUID branchId
    );

    @Query("""
            select cd from CalendarDay cd
            join fetch cd.calendarEvent e
            left join fetch e.branch
            where cd.schoolYear = :schoolYear
              and (:branchId is null or e.applyToAllBranches = true or e.branch.id = :branchId)
            order by cd.dayDate asc, e.startDate asc, e.id asc
            """)
    List<CalendarDay> findForExportBySchoolYear(
            @Param("schoolYear") String schoolYear,
            @Param("branchId") UUID branchId
    );
}
