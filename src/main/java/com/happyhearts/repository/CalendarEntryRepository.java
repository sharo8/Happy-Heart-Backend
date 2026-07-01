package com.happyhearts.repository;

import com.happyhearts.enums.CalendarEntryType;
import com.happyhearts.model.CalendarEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CalendarEntryRepository extends JpaRepository<CalendarEntry, UUID> {

    @Query("""
            select distinct e
            from CalendarEntry e
            left join e.branches b
            where
              e.startDate <= :to
              and e.endDate >= :from
              and (e.appliesToAll = true or b.id = :branchId)
              and (:type is null or e.type = :type)
            order by e.startDate asc
            """)
    List<CalendarEntry> findOverlapping(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("branchId") UUID branchId,
            @Param("type") CalendarEntryType type
    );
}

