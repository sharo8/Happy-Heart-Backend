package com.happyhearts.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkCalendarEntryRequest {

    @NotEmpty
    private List<@Valid CreateCalendarEntryRequest> entries;
}
