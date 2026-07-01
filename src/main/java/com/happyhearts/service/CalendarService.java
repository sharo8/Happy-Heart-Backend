package com.happyhearts.service;

import com.happyhearts.dto.request.CreateCalendarEntryRequest;
import com.happyhearts.dto.response.CalendarDayEventResponse;
import com.happyhearts.dto.response.CalendarDayResponse;
import com.happyhearts.dto.response.CalendarEntryResponse;
import com.happyhearts.enums.CalendarEntryType;
import com.happyhearts.enums.EventType;
import com.happyhearts.model.Branch;
import com.happyhearts.model.CalendarDay;
import com.happyhearts.model.CalendarEntry;
import com.happyhearts.model.CalendarEvent;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.CalendarDayRepository;
import com.happyhearts.repository.CalendarEntryRepository;
import com.happyhearts.repository.CalendarEventRepository;
import com.happyhearts.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CalendarEntryRepository calendarEntryRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final CalendarDayRepository calendarDayRepository;
    private final BranchRepository branchRepository;
    private final CalendarAccessService calendarAccessService;
    private final CalendarPdfParserService pdfParserService;
    private final CalendarEventColorService calendarEventColorService;
    private final CalendarSchoolDisplayMapper calendarSchoolDisplayMapper;

    private static final List<CalendarEntryType> PRIORITY = List.of(
            CalendarEntryType.SCHOOL_CLOSURE,
            CalendarEntryType.HOLIDAY,
            CalendarEntryType.RELIGIOUS_HOLIDAY,
            CalendarEntryType.SCHOOL_BREAK,
            CalendarEntryType.CLOSURE,
            CalendarEntryType.EXAM,
            CalendarEntryType.GRADUATION,
            CalendarEntryType.SUMMER_CAMP,
            CalendarEntryType.PREPARATION,
            CalendarEntryType.EVENT,
            CalendarEntryType.BACK_TO_SCHOOL,
            CalendarEntryType.MODIFIED,
            CalendarEntryType.OPEN
    );

    @Transactional
    public CalendarEntryResponse create(UserPrincipal principal, CreateCalendarEntryRequest req) {
        calendarAccessService.assertCanEdit(principal);
        if (req.getEndDate().isBefore(req.getStartDate())) {
            throw new IllegalArgumentException("endDate must be >= startDate");
        }

        Set<Branch> branches = resolveBranches(req);

        CalendarEntry row = CalendarEntry.builder()
                .type(req.getType())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .labelEn(req.getLabelEn())
                .labelFr(req.getLabelFr())
                .labelKi(req.getLabelKi())
                .appliesToAll(req.isAppliesToAll())
                .branches(branches)
                .build();

        row = calendarEntryRepository.save(row);
        return mapEntry(row);
    }

    @Transactional(readOnly = true)
    public List<CalendarEntryResponse> listEntries(UserPrincipal principal, UUID requestedBranchId, LocalDate from, LocalDate to) {
        UUID effectiveBranchId = resolveEffectiveBranch(principal, requestedBranchId);
        if (effectiveBranchId == null) {
            throw new IllegalArgumentException("branchId is required for this role");
        }
        calendarAccessService.assertCanView(principal, effectiveBranchId);
        return calendarEntryRepository.findOverlapping(from, to, effectiveBranchId, null)
                .stream()
                .map(this::mapEntry)
                .toList();
    }

    @Transactional
    public CalendarEntryResponse update(UserPrincipal principal, UUID id, CreateCalendarEntryRequest req) {
        calendarAccessService.assertCanEdit(principal);
        if (req.getEndDate().isBefore(req.getStartDate())) {
            throw new IllegalArgumentException("endDate must be >= startDate");
        }

        CalendarEntry row = calendarEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("calendar entry not found"));
        row.setType(req.getType());
        row.setStartDate(req.getStartDate());
        row.setEndDate(req.getEndDate());
        row.setLabelEn(req.getLabelEn());
        row.setLabelFr(req.getLabelFr());
        row.setLabelKi(req.getLabelKi());
        row.setAppliesToAll(req.isAppliesToAll());
        row.setBranches(resolveBranches(req));
        return mapEntry(calendarEntryRepository.save(row));
    }

    @Transactional
    public List<CalendarEntryResponse> bulkCreate(UserPrincipal principal, List<CreateCalendarEntryRequest> requests) {
        calendarAccessService.assertCanEdit(principal);
        List<CalendarEntryResponse> created = new ArrayList<>();
        for (CreateCalendarEntryRequest request : requests) {
            created.add(create(principal, request));
        }
        return created;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID id) {
        calendarAccessService.assertCanEdit(principal);
        if (!calendarEntryRepository.existsById(id)) {
            return;
        }
        calendarEntryRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<CalendarDayResponse> days(UserPrincipal principal, UUID requestedBranchId, LocalDate from, LocalDate to) {
        UUID effectiveBranchId = resolveEffectiveBranch(principal, requestedBranchId);
        if (effectiveBranchId == null) {
            throw new IllegalArgumentException("branchId is required for this role");
        }
        calendarAccessService.assertCanView(principal, effectiveBranchId);

        List<CalendarEntry> entries = calendarEntryRepository.findOverlapping(from, to, effectiveBranchId, null);
        List<CalendarEvent> schoolEvents = calendarEventRepository.findOverlapping(from, to, effectiveBranchId);

        Map<LocalDate, List<CalendarEntry>> byDay = new HashMap<>();
        for (CalendarEntry e : entries) {
            LocalDate s = e.getStartDate().isBefore(from) ? from : e.getStartDate();
            LocalDate end = e.getEndDate().isAfter(to) ? to : e.getEndDate();
            for (LocalDate d = s; !d.isAfter(end); d = d.plusDays(1)) {
                byDay.computeIfAbsent(d, k -> new ArrayList<>()).add(e);
            }
        }

        Map<LocalDate, List<CalendarEvent>> schoolByDayOverlap = new HashMap<>();
        for (CalendarEvent se : schoolEvents) {
            LocalDate s0 = se.getStartDate();
            LocalDate e0 = se.getEndDate() != null ? se.getEndDate() : se.getStartDate();
            LocalDate s = s0.isBefore(from) ? from : s0;
            LocalDate end = e0.isAfter(to) ? to : e0;
            for (LocalDate d = s; !d.isAfter(end); d = d.plusDays(1)) {
                schoolByDayOverlap.computeIfAbsent(d, k -> new ArrayList<>()).add(se);
            }
        }

        List<CalendarDay> materialized = calendarDayRepository.findMaterializedInRange(from, to, effectiveBranchId);
        Map<LocalDate, Map<UUID, CalendarEvent>> schoolByMaterializedDay = new HashMap<>();
        for (CalendarDay cd : materialized) {
            CalendarEvent ev = cd.getCalendarEvent();
            schoolByMaterializedDay
                    .computeIfAbsent(cd.getDayDate(), k -> new LinkedHashMap<>())
                    .putIfAbsent(ev.getId(), ev);
        }

        Map<LocalDate, List<CalendarEvent>> schoolByDay = new HashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Map<UUID, CalendarEvent> mat = schoolByMaterializedDay.get(d);
            if (mat != null && !mat.isEmpty()) {
                schoolByDay.put(d, new ArrayList<>(mat.values()));
            } else {
                schoolByDay.put(d, schoolByDayOverlap.getOrDefault(d, List.of()));
            }
        }

        List<CalendarDayResponse> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<CalendarEntry> dayEntries = byDay.getOrDefault(d, List.of());
            List<CalendarEvent> daySchool = schoolByDay.getOrDefault(d, List.of());
            if (dayEntries.isEmpty() && daySchool.isEmpty()) {
                out.add(CalendarDayResponse.builder()
                        .date(d)
                        .dominantType(CalendarEntryType.OPEN)
                        .dominantLabel("")
                        .events(List.of())
                        .build());
                continue;
            }

            CalendarEntryType dominant = pickDominantCombined(dayEntries, daySchool);
            List<CalendarDayEventResponse> events = new ArrayList<>();
            dayEntries.stream()
                    .filter(e -> e.getType() == dominant)
                    .sorted(Comparator.comparing(CalendarEntry::getStartDate))
                    .forEach(e -> events.add(mapEvent(e)));
            daySchool.stream()
                    .filter(s -> calendarSchoolDisplayMapper.toEntryType(s.getEventType()) == dominant)
                    .sorted(Comparator.comparing(CalendarEvent::getStartDate))
                    .forEach(s -> events.add(mapSchoolEvent(s)));

            CalendarDayResponse.CalendarDayResponseBuilder dayBuilder = CalendarDayResponse.builder()
                    .date(d)
                    .dominantType(dominant)
                    .dominantLabel(events.isEmpty() ? "" : events.get(0).getLabel())
                    .events(events);
            applySchoolDayCellFields(dayBuilder, d, daySchool);
            out.add(dayBuilder.build());
        }
        return out;
    }

    /**
     * Upload => parse => (best-effort) create entries for requested scope.
     */
    @Transactional
    public List<CalendarEntryResponse> importFromPdf(
            UserPrincipal principal,
            byte[] pdfBytes,
            boolean appliesToAll,
            List<UUID> branchIds,
            int referenceYear,
            String preferredLanguage
    ) {
        calendarAccessService.assertCanEdit(principal);

        List<CreateCalendarEntryRequest> parsed = pdfParserService.parse(
                pdfBytes,
                referenceYear,
                preferredLanguage
        );

        // Normalize scope from UI (we ignore any branch scope found in the PDF, if any).
        List<CalendarEntryResponse> created = new ArrayList<>();
        for (CreateCalendarEntryRequest p : parsed) {
            CreateCalendarEntryRequest scoped = CreateCalendarEntryRequest.builder()
                    .type(p.getType())
                    .startDate(p.getStartDate())
                    .endDate(p.getEndDate())
                    .labelEn(p.getLabelEn())
                    .labelFr(p.getLabelFr())
                    .labelKi(p.getLabelKi())
                    .appliesToAll(appliesToAll)
                    .branchIds(branchIds)
                    .build();
            created.add(create(principal, scoped));
        }
        return created;
    }

    private UUID resolveEffectiveBranch(UserPrincipal principal, UUID requestedBranchId) {
        if (principal.getRole() == com.happyhearts.enums.Role.SUPER_ADMIN) {
            return requestedBranchId;
        }
        if (principal.getRole() == com.happyhearts.enums.Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            // GMP may want one branch at a time; we still require explicit branchId.
            return requestedBranchId;
        }
        if (requestedBranchId != null) {
            return requestedBranchId;
        }
        return principal.getBranchId();
    }

    private Set<Branch> resolveBranches(CreateCalendarEntryRequest req) {
        if (req.isAppliesToAll()) {
            return Set.of();
        }
        List<UUID> branchIds = req.getBranchIds();
        if (branchIds == null || branchIds.isEmpty()) {
            throw new IllegalArgumentException("branchIds required when appliesToAll=false");
        }
        return Set.copyOf(branchRepository.findAllById(branchIds));
    }

    /**
     * Cell styling from {@link CalendarEvent}: same background on each day of the range;
     * title and tentative flag only on the event start date.
     * <p>When several events overlap on one day: prefer one that starts that day, then a longer span,
     * then a fixed type priority (e.g. break over a generic event).</p>
     */
    private void applySchoolDayCellFields(
            CalendarDayResponse.CalendarDayResponseBuilder builder,
            LocalDate d,
            List<CalendarEvent> daySchool
    ) {
        if (daySchool == null || daySchool.isEmpty()) {
            return;
        }
        CalendarEvent chosen = pickDisplaySchoolEvent(d, daySchool);
        if (chosen == null) {
            return;
        }
        EventType displayType = chosen.getEventType();
        String bg = chosen.getColorBg();
        String text = chosen.getColorText();
        if (bg == null || bg.isBlank() || text == null || text.isBlank()) {
            bg = calendarEventColorService.getBg(displayType);
            text = calendarEventColorService.getText(displayType);
        }
        builder.eventType(displayType.name())
                .eventColorBg(bg)
                .eventColorText(text);
        if (d.equals(chosen.getStartDate())) {
            builder.eventTitle(chosen.getTitle())
                    .isTentative(chosen.isTentative());
        }
    }

    private CalendarEvent pickDisplaySchoolEvent(LocalDate d, List<CalendarEvent> daySchool) {
        return daySchool.stream()
                .min(overlapDisplayComparator(d))
                .orElse(null);
    }

    /**
     * "Smallest" wins: events starting on {@code d} beat continuations; then wider spans; then higher-impact types.
     */
    private Comparator<CalendarEvent> overlapDisplayComparator(LocalDate d) {
        return Comparator.comparing((CalendarEvent e) -> !e.getStartDate().equals(d))
                .thenComparing(this::inclusiveSpanDays, Comparator.reverseOrder())
                .thenComparing(CalendarService::eventTypeOverlapRank)
                .thenComparing(CalendarEvent::getStartDate)
                .thenComparing(CalendarEvent::getId);
    }

    private long inclusiveSpanDays(CalendarEvent e) {
        LocalDate end = e.getEndDate() != null ? e.getEndDate() : e.getStartDate();
        return ChronoUnit.DAYS.between(e.getStartDate(), end) + 1;
    }

    private static int eventTypeOverlapRank(CalendarEvent e) {
        return switch (e.getEventType()) {
            case BREAK -> 0;
            case EXAM -> 1;
            case GRADUATION, CAMP -> 2;
            case TERM_HEADER -> 3;
            case HOLIDAY, PREPARATION -> 4;
            case OBSERVATION, TENTATIVE -> 5;
            case WEEKEND -> 6;
            case EVENT, SCHOOL_DAY -> 7;
        };
    }

    private CalendarEntryType pickDominantCombined(List<CalendarEntry> legacy, List<CalendarEvent> school) {
        for (CalendarEntryType type : PRIORITY) {
            boolean legacyHit = legacy.stream().anyMatch(e -> e.getType() == type);
            boolean schoolHit = school.stream().anyMatch(s -> calendarSchoolDisplayMapper.toEntryType(s.getEventType()) == type);
            if (legacyHit || schoolHit) {
                return type;
            }
        }
        return CalendarEntryType.OPEN;
    }

    private CalendarDayEventResponse mapSchoolEvent(CalendarEvent s) {
        return CalendarDayEventResponse.builder()
                .entryId(s.getId())
                .type(calendarSchoolDisplayMapper.toEntryType(s.getEventType()))
                .label(s.getTitle())
                .entrySource("SCHOOL")
                .schoolEventType(s.getEventType().name())
                .tentative(s.isTentative())
                .build();
    }

    private CalendarDayEventResponse mapEvent(CalendarEntry e) {
        Locale locale = LocaleContextHolder.getLocale();
        String label = labelForLocale(locale, e.getLabelEn(), e.getLabelFr(), e.getLabelKi());
        return CalendarDayEventResponse.builder()
                .entryId(e.getId())
                .type(e.getType())
                .label(label)
                .entrySource("LEGACY")
                .schoolEventType(null)
                .tentative(false)
                .build();
    }

    private CalendarEntryResponse mapEntry(CalendarEntry e) {
        return CalendarEntryResponse.builder()
                .id(e.getId())
                .type(e.getType())
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .labelEn(e.getLabelEn())
                .labelFr(e.getLabelFr())
                .labelKi(e.getLabelKi())
                .appliesToAll(e.isAppliesToAll())
                .branchIds(e.getBranches().stream().map(Branch::getId).toList())
                .build();
    }

    private String labelForLocale(Locale locale, String labelEn, String labelFr, String labelKi) {
        if (locale == null) return firstNonBlank(labelEn, labelFr, labelKi);
        String lang = locale.getLanguage();
        return switch (lang) {
            case "fr" -> firstNonBlank(labelFr, labelEn, labelKi);
            case "ki", "rw" -> firstNonBlank(labelKi, labelEn, labelFr);
            default -> firstNonBlank(labelEn, labelFr, labelKi);
        };
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }
}

