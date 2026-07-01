package com.happyhearts.dto.response;

import com.happyhearts.enums.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDailyRowResponse {

    private UUID employeeId;
    private String employeeName;
    private AttendanceStatus status;
    private LocalTime entryTime;
    private LocalTime exitTime;
    private BigDecimal totalHours;
    private boolean late;
    private int lateMinutes;
}
