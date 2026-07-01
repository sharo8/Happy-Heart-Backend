package com.happyhearts.repository;

import com.happyhearts.model.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    @Query("""
            select distinct e from CalendarEvent e
            where e.startDate <= :to
              and coalesce(e.endDate, e.startDate) >= :from
              and (e.applyToAllBranches = true or e.branch.id = :branchId)
            order by e.startDate asc, e.id asc
            """)
    List<CalendarEvent> findOverlapping(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("branchId") UUID branchId
    );

    @Query("""
            select e from CalendarEvent e
            where e.schoolYear = :schoolYear
              and (e.applyToAllBranches = true or (e.branch is not null and e.branch.id = :branchId))
              and e.startDate <= :monthEnd
              and coalesce(e.endDate, e.startDate) >= :monthStart
            order by e.startDate asc, e.id asc
            """)
    List<CalendarEvent> findForMonth(
            @Param("schoolYear") String schoolYear,
            @Param("branchId") UUID branchId,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd
    );

    List<CalendarEvent> findByPdfImport_Id(UUID pdfImportId);

    Optional<CalendarEvent> findTopByTitleIgnoreCaseAndStartDateAndSchoolYearOrderByIdAsc(
            String title,
            LocalDate startDate,
            String schoolYear
    );

    @Query("""
            select distinct e from CalendarEvent e
            left join fetch e.branch
            where e.schoolYear = :schoolYear
              and (:branchId is null or e.applyToAllBranches = true or e.branch.id = :branchId)
            order by e.startDate asc, e.id asc
            """)
    List<CalendarEvent> findForExportBySchoolYear(
            @Param("schoolYear") String schoolYear,
            @Param("branchId") UUID branchId
    );
}
