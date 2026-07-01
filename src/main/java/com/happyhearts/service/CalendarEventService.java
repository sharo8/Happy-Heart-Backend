package com.happyhearts.service;

import com.happyhearts.dto.request.CalendarEventWriteRequest;
import com.happyhearts.dto.response.CalendarSchoolEventResponse;
import com.happyhearts.enums.CalendarEventSource;
import com.happyhearts.enums.EventType;
import com.happyhearts.enums.Role;
import com.happyhearts.exception.BranchNotFoundException;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.model.Branch;
import com.happyhearts.model.CalendarEvent;
import com.happyhearts.model.PdfImport;
import com.happyhearts.model.User;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.CalendarEventRepository;
import com.happyhearts.repository.PdfImportRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventColorService calendarEventColorService;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final PdfImportRepository pdfImportRepository;
    private final CalendarSchoolEventResponseMapper calendarSchoolEventResponseMapper;
    private final CalendarAccessService calendarAccessService;

    @Transactional(readOnly = true)
    public List<CalendarSchoolEventResponse> listByMonth(
            UserPrincipal principal,
            String schoolYear,
            int month,
            Integer year,
            UUID branchId
    ) {
        if (branchId == null) {
            throw new BusinessException("calendar.import.branch.required");
        }
        calendarAccessService.assertCanView(principal, branchId);
        int y1 = Integer.parseInt(schoolYear.substring(0, 4));
        int calendarYear = year != null ? year : (month >= 9 ? y1 : y1 + 1);
        YearMonth ym = YearMonth.of(calendarYear, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        return calendarEventRepository.findForMonth(schoolYear, branchId, monthStart, monthEnd).stream()
                .map(calendarSchoolEventResponseMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CalendarSchoolEventResponse getById(UserPrincipal principal, UUID id) {
        CalendarEvent e = calendarEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.calendar.event.not.found"));
        assertCanViewEvent(principal, e);
        return calendarSchoolEventResponseMapper.toResponse(e);
    }

    @Transactional
    public CalendarSchoolEventResponse create(UserPrincipal principal, CalendarEventWriteRequest req) {
        assertCanManageSchoolCalendar(principal);
        validateDates(req.getStartDate(), req.getEndDate());
        validateTitle(req.getTitle());
        if (existsDuplicate(req.getTitle(), req.getStartDate(), req.getSchoolYear())) {
            throw new BusinessException("error.calendar.event.duplicate");
        }
        User user = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException("error.user.not.found"));
        EventType type = resolveEventType(req);
        Branch branchEntity = resolveBranchForWrite(principal, req);
        CalendarEvent row = CalendarEvent.builder()
                .title(req.getTitle().trim())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .eventType(type)
                .colorBg(calendarEventColorService.getBg(type))
                .colorText(calendarEventColorService.getText(type))
                .tentative(req.isTentative())
                .schoolYear(req.getSchoolYear().trim())
                .period(null)
                .periodName(req.getPeriodName() != null && !req.getPeriodName().isBlank()
                        ? req.getPeriodName().trim()
                        : detectPeriod(req.getStartDate()))
                .applyToAllBranches(req.isApplyToAllBranches())
                .branch(branchEntity)
                .source(CalendarEventSource.MANUAL)
                .pdfImport(null)
                .createdBy(user)
                .build();
        row = calendarEventRepository.save(row);
        return calendarSchoolEventResponseMapper.toResponse(row);
    }

    @Transactional
    public CalendarSchoolEventResponse update(UserPrincipal principal, UUID id, CalendarEventWriteRequest req) {
        assertCanManageSchoolCalendar(principal);
        validateDates(req.getStartDate(), req.getEndDate());
        validateTitle(req.getTitle());
        CalendarEvent e = calendarEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.calendar.event.not.found"));
        if (existsDuplicateForAnotherId(req.getTitle(), req.getStartDate(), req.getSchoolYear(), id)) {
            throw new BusinessException("error.calendar.event.duplicate");
        }
        EventType type = resolveEventType(req);
        Branch branchEntity = resolveBranchForWrite(principal, req);
        e.setTitle(req.getTitle().trim());
        e.setStartDate(req.getStartDate());
        e.setEndDate(req.getEndDate());
        e.setEventType(type);
        e.setColorBg(calendarEventColorService.getBg(type));
        e.setColorText(calendarEventColorService.getText(type));
        e.setTentative(req.isTentative());
        e.setSchoolYear(req.getSchoolYear().trim());
        e.setPeriodName(req.getPeriodName() != null && !req.getPeriodName().isBlank()
                ? req.getPeriodName().trim()
                : detectPeriod(req.getStartDate()));
        e.setApplyToAllBranches(req.isApplyToAllBranches());
        e.setBranch(branchEntity);
        e = calendarEventRepository.save(e);
        return calendarSchoolEventResponseMapper.toResponse(e);
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID id) {
        if (principal.getRole() != Role.SUPER_ADMIN) {
            throw new com.happyhearts.exception.AccessDeniedException();
        }
        if (!calendarEventRepository.existsById(id)) {
            throw new ResourceNotFoundException("error.calendar.event.not.found");
        }
        calendarEventRepository.deleteById(id);
    }

    @Transactional
    public int deleteByImport(UserPrincipal principal, UUID importId) {
        if (principal.getRole() != Role.SUPER_ADMIN) {
            throw new com.happyhearts.exception.AccessDeniedException();
        }
        PdfImport imp = pdfImportRepository.findById(importId)
                .orElseThrow(() -> new ResourceNotFoundException("error.calendar.import.not.found"));
        List<CalendarEvent> rows = calendarEventRepository.findByPdfImport_Id(importId);
        calendarEventRepository.deleteAll(rows);
        pdfImportRepository.delete(imp);
        return rows.size();
    }

    public boolean existsDuplicate(String title, LocalDate startDate, String schoolYear) {
        return calendarEventRepository
                .findTopByTitleIgnoreCaseAndStartDateAndSchoolYearOrderByIdAsc(title.trim(), startDate, schoolYear)
                .isPresent();
    }

    private boolean existsDuplicateForAnotherId(String title, LocalDate startDate, String schoolYear, UUID id) {
        Optional<CalendarEvent> o = calendarEventRepository
                .findTopByTitleIgnoreCaseAndStartDateAndSchoolYearOrderByIdAsc(title.trim(), startDate, schoolYear);
        return o.filter(existing -> !existing.getId().equals(id)).isPresent();
    }

    private void assertCanViewEvent(UserPrincipal principal, CalendarEvent e) {
        if (principal.getRole() == Role.SUPER_ADMIN || principal.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            return;
        }
        if (e.isApplyToAllBranches()) {
            if (principal.getBranchId() == null) {
                throw new com.happyhearts.exception.AccessDeniedException();
            }
            calendarAccessService.assertCanView(principal, principal.getBranchId());
            return;
        }
        UUID bid = e.getBranch() != null ? e.getBranch().getId() : null;
        if (bid == null) {
            throw new com.happyhearts.exception.AccessDeniedException();
        }
        calendarAccessService.assertCanView(principal, bid);
    }


    private void assertCanManageSchoolCalendar(UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        throw new com.happyhearts.exception.AccessDeniedException();
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (end != null && end.isBefore(start)) {
            throw new BusinessException("error.calendar.event.end.before.start");
        }
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("error.calendar.event.title.required");
        }
    }

    private EventType resolveEventType(CalendarEventWriteRequest req) {
        if (req.getEventType() != null) {
            return req.getEventType();
        }
        return calendarEventColorService.detectFromTitle(req.getTitle(), req.isTentative());
    }

    private Branch resolveBranchForWrite(UserPrincipal principal, CalendarEventWriteRequest req) {
        if (req.isApplyToAllBranches()) {
            return null;
        }
        UUID bid = req.getBranchId();
        if (bid == null) {
            throw new BusinessException("calendar.import.branch.required");
        }
        if (principal.getRole() == Role.CENTRAL_COORDINATOR) {
            if (principal.getBranchId() == null || !principal.getBranchId().equals(bid)) {
                throw new com.happyhearts.exception.AccessDeniedException();
            }
        }
        return branchRepository.findById(bid).orElseThrow(BranchNotFoundException::new);
    }

    private static String detectPeriod(LocalDate date) {
        if (date == null) {
            return null;
        }
        int m = date.getMonthValue();
        if (m >= 9 && m <= 12) {
            return "TERM I";
        }
        if (m >= 1 && m <= 3) {
            return "TERM II";
        }
        if (m >= 4 && m <= 6) {
            return "TERM III";
        }
        if (m >= 7 && m <= 8) {
            return "SUMMER";
        }
        return null;
    }
}
