package com.happyhearts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.happyhearts.dto.response.CalendarSchoolEventResponse;
import com.happyhearts.dto.response.PdfImportResultResponse;
import com.happyhearts.enums.CalendarEventSource;
import com.happyhearts.enums.EventType;
import com.happyhearts.enums.PdfImportStatus;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.model.Branch;
import com.happyhearts.model.CalendarEvent;
import com.happyhearts.model.PdfImport;
import com.happyhearts.model.SchoolPeriod;
import com.happyhearts.model.User;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.CalendarEventRepository;
import com.happyhearts.repository.PdfImportRepository;
import com.happyhearts.repository.SchoolPeriodRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarPdfImportService {

    private final SchoolYearPdfCalendarParserService parser;
    private final PdfImportRepository pdfImportRepository;
    private final SchoolPeriodRepository schoolPeriodRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final ObjectMapper objectMapper;
    private final CalendarEventColorService calendarEventColorService;
    private final CalendarSchoolEventResponseMapper calendarSchoolEventResponseMapper;

    private final CalendarPdfDayMaterializationService calendarPdfDayMaterializationService;

    @Transactional
    public PdfImportResultResponse importPdf(
            UserPrincipal principal,
            MultipartFile file,
            int academicYearStart,
            boolean applyToAllBranches,
            UUID branchId
    ) throws IOException {
        User user = userRepository.findById(principal.getId()).orElseThrow();
        Branch branchEntity = (!applyToAllBranches && branchId != null)
                ? branchRepository.findById(branchId).orElseThrow()
                : null;

        PdfImport imp = PdfImport.builder()
                .filename(Objects.requireNonNullElse(file.getOriginalFilename(), "calendar.pdf"))
                .schoolYear(SchoolYearPdfCalendarParserService.schoolYearLabel(academicYearStart))
                .importedBy(user)
                .applyToAllBranches(applyToAllBranches)
                .branch(branchEntity)
                .status(PdfImportStatus.PROCESSING)
                .eventsCreated(0)
                .periodsCreated(0)
                .build();
        imp = pdfImportRepository.save(imp);

        try {
            SchoolYearPdfCalendarParserService.ParsedCalendar parsed = parser.parse(file.getBytes(), academicYearStart);
            if (parsed.events().isEmpty()) {
                failImport(imp, parsed.rawText(), "NO_EVENTS");
                throw new BusinessException("calendar.import.no.events");
            }

            Map<String, List<SchoolYearPdfCalendarParserService.ParsedEvent>> byPeriod = new LinkedHashMap<>();
            for (SchoolYearPdfCalendarParserService.ParsedEvent e : parsed.events()) {
                byPeriod.computeIfAbsent(e.periodKey(), k -> new ArrayList<>()).add(e);
            }

            Map<String, SchoolPeriod> periodEntityByKey = new HashMap<>();
            int periodsCreated = 0;
            for (var entry : byPeriod.entrySet()) {
                List<SchoolYearPdfCalendarParserService.ParsedEvent> list = entry.getValue();
                if (list.isEmpty()) {
                    continue;
                }
                LocalDate mn = parser.periodBoundsMin(list);
                LocalDate mx = parser.periodBoundsMax(list);
                if (mn == null || mx == null) {
                    continue;
                }
                SchoolPeriod sp = SchoolPeriod.builder()
                        .name(entry.getKey())
                        .schoolYear(parsed.schoolYear())
                        .startDate(mn)
                        .endDate(mx)
                        .color("#FF6B35")
                        .applyToAllBranches(applyToAllBranches)
                        .branch(branchEntity)
                        .createdBy(user)
                        .build();
                sp = schoolPeriodRepository.save(sp);
                periodEntityByKey.put(entry.getKey(), sp);
                periodsCreated++;
            }

            List<CalendarSchoolEventResponse> responseEvents = new ArrayList<>();
            for (SchoolYearPdfCalendarParserService.ParsedEvent pe : parsed.events()) {
                if (calendarEventRepository
                        .findTopByTitleIgnoreCaseAndStartDateAndSchoolYearOrderByIdAsc(
                                pe.title(), pe.startDate(), parsed.schoolYear())
                        .isPresent()) {
                    log.warn("Skip duplicate school calendar event: {} @ {}", pe.title(), pe.startDate());
                    continue;
                }
                SchoolPeriod period = periodEntityByKey.get(pe.periodKey());
                LocalDate endDb = pe.endDate().equals(pe.startDate()) ? null : pe.endDate();
                EventType pdfType = pe.pdfEventType();
                String[] colors = new String[]{
                        calendarEventColorService.getBg(pdfType),
                        calendarEventColorService.getText(pdfType)
                };
                String periodName = periodLabelForEvent(pe.periodKey());
                CalendarEvent row = CalendarEvent.builder()
                        .title(pe.title())
                        .startDate(pe.startDate())
                        .endDate(endDb)
                        .eventType(pdfType)
                        .colorBg(colors[0])
                        .colorText(colors[1])
                        .tentative(pe.tentative())
                        .schoolYear(parsed.schoolYear())
                        .period(period)
                        .periodName(periodName)
                        .applyToAllBranches(applyToAllBranches)
                        .branch(branchEntity)
                        .source(CalendarEventSource.PDF_IMPORT)
                        .pdfImport(imp)
                        .createdBy(user)
                        .build();
                row = calendarEventRepository.save(row);
                calendarPdfDayMaterializationService.materializePdfEvent(row);
                responseEvents.add(calendarSchoolEventResponseMapper.toResponse(row));
            }

            if (responseEvents.isEmpty()) {
                failImport(imp, parsed.rawText(), "ALL_DUPLICATES");
                throw new BusinessException("calendar.import.no.events");
            }

            imp.setStatus(PdfImportStatus.SUCCESS);
            imp.setEventsCreated(responseEvents.size());
            imp.setPeriodsCreated(periodsCreated);
            imp.setRawText(parsed.rawText());
            imp.setParsedDataJson(writeParsedJson(parsed));
            imp.setCompletedAt(Instant.now());
            pdfImportRepository.save(imp);

            return PdfImportResultResponse.builder()
                    .importId(imp.getId())
                    .schoolYear(parsed.schoolYear())
                    .eventsCreated(responseEvents.size())
                    .periodsCreated(periodsCreated)
                    .events(responseEvents)
                    .periodNames(new ArrayList<>(byPeriod.keySet()))
                    .message("")
                    .build();

        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("PDF calendar import failed", ex);
            failImport(imp, null, ex.getMessage());
            throw new BusinessException("calendar.import.failed");
        }
    }

    private void failImport(PdfImport imp, String rawText, String err) {
        imp.setStatus(PdfImportStatus.FAILED);
        imp.setErrorMessage(err);
        if (rawText != null) {
            imp.setRawText(rawText);
        }
        imp.setCompletedAt(Instant.now());
        pdfImportRepository.save(imp);
    }

    private String writeParsedJson(SchoolYearPdfCalendarParserService.ParsedCalendar parsed) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "schoolYear", parsed.schoolYear(),
                    "eventCount", parsed.events().size()
            ));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public CalendarSchoolEventResponse toResponse(CalendarEvent e) {
        return calendarSchoolEventResponseMapper.toResponse(e);
    }

    private static String periodLabelForEvent(String periodKey) {
        if (periodKey == null || periodKey.isBlank() || "GENERAL".equalsIgnoreCase(periodKey)) {
            return null;
        }
        return periodKey;
    }
}
