package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.request.BulkCalendarEntryRequest;
import com.happyhearts.dto.request.CalendarEventWriteRequest;
import com.happyhearts.dto.request.CreateCalendarEntryRequest;
import com.happyhearts.dto.response.CalendarDayResponse;
import com.happyhearts.dto.response.CalendarEntryResponse;
import com.happyhearts.dto.response.CalendarSchoolEventResponse;
import com.happyhearts.dto.response.PdfImportResultResponse;
import com.happyhearts.enums.Role;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.CalendarEventService;
import com.happyhearts.service.CalendarPdfImportService;
import com.happyhearts.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;
    private final CalendarPdfImportService calendarPdfImportService;
    private final CalendarEventService calendarEventService;
    private final MessageSource messageSource;

    @GetMapping("/days")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER','ASSISTANT','COOK','CLEANER','TEACHER')")
    public ResponseEntity<ApiResponse<List<CalendarDayResponse>>> days(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) UUID branchId
    ) {
        List<CalendarDayResponse> data = calendarService.days(principal, branchId, from, to);
        String msg = messageSource.getMessage("calendar.list.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER','ASSISTANT','COOK','CLEANER','TEACHER')")
    public ResponseEntity<ApiResponse<List<CalendarSchoolEventResponse>>> listSchoolCalendarEvents(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String schoolYear,
            @RequestParam int month,
            @RequestParam(required = false) Integer year,
            @RequestParam UUID branchId
    ) {
        List<CalendarSchoolEventResponse> data = calendarEventService.listByMonth(
                principal, schoolYear, month, year, branchId);
        String msg = messageSource.getMessage("calendar.school.events.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/events/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER','ASSISTANT','COOK','CLEANER','TEACHER')")
    public ResponseEntity<ApiResponse<CalendarSchoolEventResponse>> getSchoolCalendarEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        CalendarSchoolEventResponse data = calendarEventService.getById(principal, id);
        String msg = messageSource.getMessage("calendar.school.event.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping("/events")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CalendarSchoolEventResponse>> createSchoolCalendarEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CalendarEventWriteRequest request
    ) {
        CalendarSchoolEventResponse data = calendarEventService.create(principal, request);
        String msg = messageSource.getMessage("calendar.school.event.create.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PutMapping("/events/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CalendarSchoolEventResponse>> updateSchoolCalendarEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CalendarEventWriteRequest request
    ) {
        CalendarSchoolEventResponse data = calendarEventService.update(principal, id, request);
        String msg = messageSource.getMessage("calendar.school.event.update.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @DeleteMapping("/events/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSchoolCalendarEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        calendarEventService.delete(principal, id);
        String msg = messageSource.getMessage("calendar.school.event.delete.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }

    @DeleteMapping("/import/{importId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> deletePdfImport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID importId
    ) {
        int deleted = calendarEventService.deleteByImport(principal, importId);
        String msg = messageSource.getMessage(
                "calendar.import.delete.success",
                new Object[]{deleted},
                LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, deleted));
    }

    @PostMapping(value = "/import-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CENTRAL_COORDINATOR','LEAD_TEACHER')")
    public ResponseEntity<ApiResponse<PdfImportResultResponse>> importSchoolCalendarPdf(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("pdf") MultipartFile pdf,
            @RequestParam int referenceYear,
            @RequestParam(defaultValue = "true") boolean applyToAllBranches,
            @RequestParam(required = false) UUID branchId
    ) throws IOException {
        if (principal.getRole() == Role.LEAD_TEACHER || principal.getRole() == Role.CENTRAL_COORDINATOR) {
            applyToAllBranches = false;
            branchId = principal.getBranchId();
        }
        if (pdf == null || pdf.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(
                    messageSource.getMessage("calendar.import.no.file", null, LocaleContextHolder.getLocale())));
        }
        String ct = pdf.getContentType();
        if (ct == null || !ct.equalsIgnoreCase("application/pdf")) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(
                    messageSource.getMessage("calendar.import.not.pdf", null, LocaleContextHolder.getLocale())));
        }
        if (referenceYear < 2000 || referenceYear > 2100) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(
                    messageSource.getMessage("calendar.import.bad.year", null, LocaleContextHolder.getLocale())));
        }
        if (!applyToAllBranches && branchId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(
                    messageSource.getMessage("calendar.import.branch.required", null, LocaleContextHolder.getLocale())));
        }

        PdfImportResultResponse data = calendarPdfImportService.importPdf(
                principal, pdf, referenceYear, applyToAllBranches, branchId);
        String msg = messageSource.getMessage(
                "calendar.import.pdf.success",
                new Object[]{data.getEventsCreated(), data.getSchoolYear()},
                LocaleContextHolder.getLocale());
        PdfImportResultResponse withMsg = PdfImportResultResponse.builder()
                .importId(data.getImportId())
                .schoolYear(data.getSchoolYear())
                .eventsCreated(data.getEventsCreated())
                .periodsCreated(data.getPeriodsCreated())
                .events(data.getEvents())
                .periodNames(data.getPeriodNames())
                .message(msg)
                .build();
        return ResponseEntity.ok(ApiResponse.ok(msg, withMsg));
    }

    @PostMapping(value = "/entries/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CENTRAL_COORDINATOR','LEAD_TEACHER')")
    public ResponseEntity<ApiResponse<List<CalendarEntryResponse>>> uploadPdf(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam int referenceYear,
            @RequestParam boolean appliesToAll,
            @RequestParam(required = false) List<UUID> branchIds,
            @RequestParam String preferredLanguage
    ) throws Exception {
        if (principal.getRole() == Role.LEAD_TEACHER || principal.getRole() == Role.CENTRAL_COORDINATOR) {
            appliesToAll = false;
            branchIds = principal.getBranchId() != null ? List.of(principal.getBranchId()) : List.of();
        }
        byte[] bytes = file.getBytes();
        List<CalendarEntryResponse> created = calendarService.importFromPdf(
                principal,
                bytes,
                appliesToAll,
                branchIds == null ? List.of() : branchIds,
                referenceYear,
                preferredLanguage
        );
        String msg = messageSource.getMessage("calendar.upload.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, created));
    }

    @PostMapping("/entries")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CalendarEntryResponse>> createEntry(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCalendarEntryRequest request
    ) {
        CalendarEntryResponse data = calendarService.create(principal, request);
        String msg = messageSource.getMessage("calendar.create.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/entries")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER','ASSISTANT','COOK','CLEANER','TEACHER')")
    public ResponseEntity<ApiResponse<List<CalendarEntryResponse>>> listEntries(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID branchId
    ) {
        LocalDate start = from;
        LocalDate end = to;
        if (start == null || end == null) {
            int y = year != null ? year : LocalDate.now().getYear();
            int m = month != null ? month : LocalDate.now().getMonthValue();
            start = LocalDate.of(y, m, 1);
            end = start.withDayOfMonth(start.lengthOfMonth());
        }
        List<CalendarEntryResponse> data = calendarService.listEntries(principal, branchId, start, end);
        String msg = messageSource.getMessage("calendar.list.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PutMapping("/entries/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CalendarEntryResponse>> updateEntry(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateCalendarEntryRequest request
    ) {
        CalendarEntryResponse data = calendarService.update(principal, id, request);
        String msg = messageSource.getMessage("calendar.create.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping("/entries/bulk")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<CalendarEntryResponse>>> bulkCreateEntries(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BulkCalendarEntryRequest request
    ) {
        List<CalendarEntryResponse> data = calendarService.bulkCreate(principal, request.getEntries());
        String msg = messageSource.getMessage("calendar.create.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @DeleteMapping("/entries/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteEntry(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        calendarService.delete(principal, id);
        String msg = messageSource.getMessage("calendar.delete.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }
}

