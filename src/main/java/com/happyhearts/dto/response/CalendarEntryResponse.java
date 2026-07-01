package com.happyhearts.dto.response;

import com.happyhearts.enums.CalendarEntryType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class CalendarEntryResponse {
    UUID id;
    CalendarEntryType type;
    LocalDate startDate;
    LocalDate endDate;

    // Labels per language for UI rendering.
    String labelEn;
    String labelFr;
    String labelKi;

    boolean appliesToAll;
    List<UUID> branchIds;
}

