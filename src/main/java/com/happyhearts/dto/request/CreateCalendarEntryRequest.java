package com.happyhearts.dto.request;

import com.happyhearts.enums.CalendarEntryType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Mutable request DTO so Jackson can bind JSON from the dashboard reliably
 * (immutable {@code @Value} + {@code @Builder} alone is easy to misconfigure for deserialization).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCalendarEntryRequest {

    @NotNull
    private CalendarEntryType type;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private String labelEn;
    private String labelFr;
    private String labelKi;

    private boolean appliesToAll;

    private List<UUID> branchIds;
}
