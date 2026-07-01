package com.happyhearts.dto.request;

import com.happyhearts.enums.ExcuseType;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateExcuseRequest {
    private UUID employeeId;
    private ExcuseType excuseType;
    private String reason;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
