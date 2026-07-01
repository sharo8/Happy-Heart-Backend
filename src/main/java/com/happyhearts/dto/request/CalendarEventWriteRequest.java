package com.happyhearts.dto.request;

import com.happyhearts.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventWriteRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    /** When null, derived from title + tentative via {@link com.happyhearts.service.CalendarEventColorService}. */
    private EventType eventType;

    @NotNull
    private LocalDate startDate;

    private LocalDate endDate;

    private boolean tentative;

    @NotBlank
    @Size(max = 9)
    private String schoolYear;

    @Size(max = 50)
    private String periodName;

    private UUID branchId;

    private boolean applyToAllBranches = true;
}
