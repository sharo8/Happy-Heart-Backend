package com.happyhearts.service;

import com.happyhearts.dto.response.CalendarSchoolEventResponse;
import com.happyhearts.model.CalendarEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CalendarSchoolEventResponseMapper {

    private final CalendarEventColorService calendarEventColorService;

    public CalendarSchoolEventResponse toResponse(CalendarEvent e) {
        String bg = e.getColorBg();
        if (bg == null || bg.isBlank()) {
            bg = calendarEventColorService.getBg(e.getEventType());
        }
        return CalendarSchoolEventResponse.builder()
                .id(e.getId())
                .title(e.getTitle())
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .eventType(e.getEventType().name())
                .tentative(e.isTentative())
                .schoolYear(e.getSchoolYear())
                .applyToAllBranches(e.isApplyToAllBranches())
                .source(e.getSource().name())
                .color(bg)
                .periodId(e.getPeriod() != null ? e.getPeriod().getId() : null)
                .build();
    }
}
