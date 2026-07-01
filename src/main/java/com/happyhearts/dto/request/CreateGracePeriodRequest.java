package com.happyhearts.dto.request;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateGracePeriodRequest {

    private UUID employeeId;
    private String employeeReason;
    private List<UUID> recipientUserIds;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String approverExplanation;
    private Boolean grantImmediately;
}
