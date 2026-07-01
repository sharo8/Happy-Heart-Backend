package com.happyhearts.service;

import com.happyhearts.enums.CalendarEntryType;
import com.happyhearts.enums.CalendarEventSource;
import com.happyhearts.model.CalendarDay;
import com.happyhearts.model.CalendarEvent;
import com.happyhearts.repository.CalendarDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Persists one {@link CalendarDay} per weekday in a PDF-imported event range so the grid matches
 * per-cell PDF colours (weekends are skipped so Saturday/Sunday keep weekend styling).
 */
@Service
@RequiredArgsConstructor
public class CalendarPdfDayMaterializationService {

    private final CalendarDayRepository calendarDayRepository;
    private final CalendarSchoolDisplayMapper calendarSchoolDisplayMapper;

    @Transactional
    public void materializePdfEvent(CalendarEvent event) {
        if (event.getSource() != CalendarEventSource.PDF_IMPORT || event.getId() == null) {
            return;
        }
        LocalDate start = event.getStartDate();
        LocalDate end = event.getEndDate() != null ? event.getEndDate() : event.getStartDate();
        CalendarEntryType dayKind = calendarSchoolDisplayMapper.toEntryType(event.getEventType());
        String bg = event.getColorBg();
        String fg = event.getColorText();
        String title = event.getTitle() != null ? event.getTitle() : "";

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                continue;
            }
            CalendarDay row = CalendarDay.builder()
                    .dayDate(d)
                    .schoolYear(event.getSchoolYear())
                    .dayKind(dayKind)
                    .colorBg(bg)
                    .colorText(fg)
                    .label(title)
                    .source(CalendarEventSource.PDF_IMPORT)
                    .calendarEvent(event)
                    .build();
            calendarDayRepository.save(row);
        }
    }
}
