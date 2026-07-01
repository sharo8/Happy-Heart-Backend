package com.happyhearts.service;

import com.happyhearts.enums.CalendarEntryType;
import com.happyhearts.enums.EventType;
import com.happyhearts.model.CalendarDay;
import com.happyhearts.model.CalendarEntry;
import com.happyhearts.model.CalendarEvent;
import com.happyhearts.model.Employee;
import com.happyhearts.repository.CalendarDayRepository;
import com.happyhearts.repository.CalendarEntryRepository;
import com.happyhearts.repository.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpectedWorkDayService {

    /**
     * Calendar types that mean staff do not work (weekends handled separately).
     * School breaks / exam periods are NOT included — staff are still expected unless explicitly closed.
     */
    private static final Set<CalendarEntryType> OFF_DAY_ENTRY_TYPES = EnumSet.of(
            CalendarEntryType.HOLIDAY,
            CalendarEntryType.RELIGIOUS_HOLIDAY,
            CalendarEntryType.SCHOOL_CLOSURE
    );

    private static final Set<EventType> OFF_DAY_EVENT_TYPES = EnumSet.of(
            EventType.HOLIDAY
    );

    private final CalendarEntryRepository calendarEntryRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final CalendarDayRepository calendarDayRepository;

    @Transactional(readOnly = true)
    public boolean isExpectedWorkDay(Employee employee, LocalDate day) {
        UUID branchId = employee.getBranch() != null ? employee.getBranch().getId() : null;
        return isExpectedWorkDay(branchId, day);
    }

    @Transactional(readOnly = true)
    public boolean isExpectedWorkDay(UUID branchId, LocalDate day) {
        if (isWeekend(day)) {
            return false;
        }
        if (branchId == null) {
            return true;
        }
        return !isCalendarOffDay(branchId, day);
    }

    @Transactional(readOnly = true)
    public OffDayIndex buildIndex(UUID branchId, LocalDate from, LocalDate to) {
        Set<LocalDate> offDays = new HashSet<>();
        if (branchId != null) {
            for (CalendarEntry entry : calendarEntryRepository.findOverlapping(from, to, branchId, null)) {
                if (!OFF_DAY_ENTRY_TYPES.contains(entry.getType())) {
                    continue;
                }
                markRange(offDays, entry.getStartDate(), entry.getEndDate(), from, to);
            }
            for (CalendarEvent event : calendarEventRepository.findOverlapping(from, to, branchId)) {
                if (!OFF_DAY_EVENT_TYPES.contains(event.getEventType())) {
                    continue;
                }
                LocalDate end = event.getEndDate() != null ? event.getEndDate() : event.getStartDate();
                markRange(offDays, event.getStartDate(), end, from, to);
            }
            for (CalendarDay materialized : calendarDayRepository.findMaterializedInRange(from, to, branchId)) {
                EventType type = materialized.getCalendarEvent().getEventType();
                if (OFF_DAY_EVENT_TYPES.contains(type)) {
                    offDays.add(materialized.getDayDate());
                }
            }
        }
        return new OffDayIndex(offDays);
    }

    private boolean isCalendarOffDay(UUID branchId, LocalDate day) {
        List<CalendarEntry> entries = calendarEntryRepository.findOverlapping(day, day, branchId, null);
        for (CalendarEntry entry : entries) {
            if (OFF_DAY_ENTRY_TYPES.contains(entry.getType())) {
                return true;
            }
        }
        List<CalendarEvent> events = calendarEventRepository.findOverlapping(day, day, branchId);
        for (CalendarEvent event : events) {
            if (OFF_DAY_EVENT_TYPES.contains(event.getEventType())) {
                return true;
            }
        }
        for (CalendarDay materialized : calendarDayRepository.findMaterializedInRange(day, day, branchId)) {
            if (OFF_DAY_EVENT_TYPES.contains(materialized.getCalendarEvent().getEventType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWeekend(LocalDate day) {
        DayOfWeek dow = day.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    private static void markRange(Set<LocalDate> offDays, LocalDate start, LocalDate end, LocalDate from, LocalDate to) {
        LocalDate s = start.isBefore(from) ? from : start;
        LocalDate e = end.isAfter(to) ? to : end;
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            if (!isWeekend(d)) {
                offDays.add(d);
            }
        }
    }

    public static final class OffDayIndex {
        private final Set<LocalDate> offDays;

        OffDayIndex(Set<LocalDate> offDays) {
            this.offDays = offDays;
        }

        public boolean isExpectedWorkDay(LocalDate day) {
            if (isWeekend(day)) {
                return false;
            }
            return !offDays.contains(day);
        }
    }
}
