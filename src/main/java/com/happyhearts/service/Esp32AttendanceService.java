package com.happyhearts.service;

import com.happyhearts.config.AttendanceProperties;
import com.happyhearts.enums.DeviceApprovalStatus;
import com.happyhearts.enums.DeviceStatus;
import com.happyhearts.enums.Language;
import com.happyhearts.enums.ReaderType;
import com.happyhearts.enums.ScanType;
import com.happyhearts.enums.SyncSource;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.exception.ResourceNotFoundException;
import com.happyhearts.model.AttendanceRecord;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Employee;
import com.happyhearts.model.OfflineSyncLog;
import com.happyhearts.model.RfidDevice;
import com.happyhearts.model.RfidReader;
import com.happyhearts.model.UnknownCard;
import com.happyhearts.repository.AttendanceRecordRepository;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.ExplanationRequestRepository;
import com.happyhearts.repository.OfflineSyncLogRepository;
import com.happyhearts.repository.RfidDeviceRepository;
import com.happyhearts.repository.RfidReaderRepository;
import com.happyhearts.repository.UnknownCardRepository;
import com.happyhearts.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class Esp32AttendanceService {

    private static final long SCAN_DEBOUNCE_MS = 3000;

    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final RfidReaderRepository rfidReaderRepository;
    private final RfidDeviceRepository rfidDeviceRepository;
    private final UnknownCardRepository unknownCardRepository;
    private final OfflineSyncLogRepository offlineSyncLogRepository;
    private final AttendanceProperties attendanceProperties;
    private final WorkScheduleResolver workScheduleResolver;
    private final AttendanceEvaluationService attendanceEvaluationService;
    private final ExpectedWorkDayService expectedWorkDayService;
    private final EmailService emailService;
    private final ExplanationRequestRepository explanationRequestRepository;
    private final ConcurrentHashMap<String, Object> uidScanLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> unknownUidLocks = new ConcurrentHashMap<>();

    @Transactional
    public Map<String, Object> processScan(String uid, String deviceId) {
        String normalizedUid = normalizeUid(uid);
        Object lock = uidScanLocks.computeIfAbsent(normalizedUid, k -> new Object());
        synchronized (lock) {
            return processScanInternal(normalizedUid, deviceId);
        }
    }

    private Map<String, Object> processScanInternal(String normalizedUid, String deviceId) {
        Employee employee = employeeRepository.findWithBranchByRfidCardUid(normalizedUid).orElse(null);
        if (employee == null) {
            throw new IllegalArgumentException("unknown_card");
        }
        if (!employee.isEmploymentActive() || !employee.isRfidActive()) {
            throw new IllegalArgumentException("inactive_employee");
        }

        RfidReader reader = resolveReader(deviceId, employee);
        Instant scannedAt = Instant.now();
        LocalDate day = TimeUtils.toKigaliDate(scannedAt);
        Instant dayStart = TimeUtils.startOfDayKigali(day);
        Instant dayEnd = TimeUtils.endOfDayKigali(day);

        List<AttendanceRecord> recentToday = attendanceRecordRepository.findTodayByEmployee(
                employee.getId(), dayStart, dayEnd);
        AttendanceRecord lastRecord = recentToday.isEmpty() ? null : recentToday.get(0);

        if (lastRecord != null) {
            long diffMs = Math.abs(Duration.between(lastRecord.getScannedAt(), scannedAt).toMillis());
            ScanType attemptedType = resolveNextScanType(employee.getId(), scannedAt);
            if (diffMs < SCAN_DEBOUNCE_MS) {
                if (isWithinToggleCooldown(lastRecord, attemptedType, scannedAt)) {
                    log.info("Toggle cooldown (debounce): {} {} -> {}", normalizedUid, lastRecord.getScanType(), attemptedType);
                    return toToggleCooldownResult(lastRecord, employee, deviceId, scannedAt, attemptedType);
                }
                log.info("Duplicate scan ignored ({}ms): {} {}", diffMs, normalizedUid, lastRecord.getScanType());
                return toScanResult(lastRecord, deviceId, true);
            }
        }

        ScanType scanType = resolveNextScanType(employee.getId(), scannedAt);

        if (lastRecord != null && isWithinToggleCooldown(lastRecord, scanType, scannedAt)) {
            log.info("Toggle blocked — cooldown active for {} ({} -> {})", normalizedUid, lastRecord.getScanType(), scanType);
            return toToggleCooldownResult(lastRecord, employee, deviceId, scannedAt, scanType);
        }

        if (isDuplicateSameTypeWindow(normalizedUid, scanType, scannedAt)) {
            AttendanceRecord existing = attendanceRecordRepository
                    .findRecentByCardAndType(normalizedUid, scanType,
                            scannedAt.minus(Duration.ofMinutes(attendanceProperties.getDuplicateScanWindowMinutes())))
                    .get(0);
            log.info("Duplicate scan type ignored: {} {}", normalizedUid, scanType);
            return toScanResult(existing, deviceId, true);
        }

        AttendanceRecord record = AttendanceRecord.builder()
                .employee(employee)
                .branch(employee.getBranch())
                .rfidReader(reader)
                .rfidCardUid(normalizedUid)
                .scanType(scanType)
                .scannedAt(scannedAt)
                .synced(false)
                .syncSource(SyncSource.ONLINE)
                .build();
        attendanceRecordRepository.save(record);

        touchDevice(deviceId, scannedAt, null);
        touchReader(reader);

        log.info("Scan recorded: {} {} {}", employee.getFirstName(), toApiScanType(scanType), normalizedUid);
        emailService.sendAttendanceScanConfirmation(employee, employee.getBranch(), scannedAt, scanType);
        return toScanResult(record, deviceId, false);
    }

    private long cooldownRequiredMs() {
        return attendanceProperties.getMinimumMinutesBeforeCheckout() * 60_000L;
    }

    /** Blocks check-out too soon after check-in, and check-in too soon after check-out. */
    private boolean isWithinToggleCooldown(AttendanceRecord lastRecord, ScanType nextScanType, Instant now) {
        if (lastRecord == null) {
            return false;
        }
        long elapsedMs = Duration.between(lastRecord.getScannedAt(), now).toMillis();
        if (elapsedMs >= cooldownRequiredMs()) {
            return false;
        }
        if (nextScanType == ScanType.EXIT && lastRecord.getScanType() == ScanType.ENTRY) {
            return true;
        }
        return nextScanType == ScanType.ENTRY && lastRecord.getScanType() == ScanType.EXIT;
    }

    private long minutesUntilToggleAllowed(AttendanceRecord lastRecord, Instant now) {
        long elapsedMs = Duration.between(lastRecord.getScannedAt(), now).toMillis();
        long remainingMs = Math.max(0, cooldownRequiredMs() - elapsedMs);
        return (remainingMs + 59_999) / 60_000;
    }

    private Map<String, Object> toToggleCooldownResult(
            AttendanceRecord lastRecord,
            Employee employee,
            String deviceId,
            Instant now,
            ScanType attemptedScanType
    ) {
        long minutesRemaining = minutesUntilToggleAllowed(lastRecord, now);
        int cooldownMinutes = attendanceProperties.getMinimumMinutesBeforeCheckout();
        String lastScanFormatted = DateTimeFormatter.ofPattern("HH:mm")
                .withZone(TimeUtils.kigali())
                .format(lastRecord.getScannedAt());
        Language lang = employee.getPreferredLanguage() != null ? employee.getPreferredLanguage() : Language.EN;
        boolean blockingCheckout = attemptedScanType == ScanType.EXIT;

        String message = switch (lang) {
            case EN -> blockingCheckout
                    ? """
                    Hello %s, you checked in at %s. Check-out will be available in %d minute(s). \
                    Please keep your card away from the reader until then."""
                    .formatted(employee.getFirstName(), lastScanFormatted, minutesRemaining)
                    : """
                    Hello %s, you checked out at %s. Check-in will be available in %d minute(s). \
                    Please wait before tapping your card again."""
                    .formatted(employee.getFirstName(), lastScanFormatted, minutesRemaining);
            case FR -> blockingCheckout
                    ? """
                    Bonjour %s, vous avez pointé votre entrée à %s. \
                    Le pointage de sortie sera disponible dans %d minute(s). Veuillez éloigner votre carte du lecteur."""
                    .formatted(employee.getFirstName(), lastScanFormatted, minutesRemaining)
                    : """
                    Bonjour %s, vous avez pointé votre sortie à %s. \
                    Le pointage d'entrée sera disponible dans %d minute(s). Veuillez patienter avant de re-scanner votre carte."""
                    .formatted(employee.getFirstName(), lastScanFormatted, minutesRemaining);
            case KI -> blockingCheckout
                    ? """
                    Muraho %s, winjiye saa %s. Gusohoka bizaboneka mu minota %d."""
                    .formatted(employee.getFirstName(), lastScanFormatted, minutesRemaining)
                    : """
                    Muraho %s, wasohotse saa %s. Kwinjira bizaboneka mu minota %d."""
                    .formatted(employee.getFirstName(), lastScanFormatted, minutesRemaining);
        };

        String errorCode = blockingCheckout ? "checkout_cooldown" : "checkin_cooldown";
        String currentStatusApi = blockingCheckout ? "check_in" : "check_out";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", errorCode);
        result.put("duplicate", false);
        result.put("message", message);
        result.put("scanType", currentStatusApi);
        result.put("attemptedScanType", toApiScanType(attemptedScanType));
        result.put("lastScanAt", lastRecord.getScannedAt().toString());
        result.put("minutesRemaining", minutesRemaining);
        result.put("cooldownMinutes", cooldownMinutes);
        result.put("device", deviceId);
        result.put("employee", employeeScanPayload(employee));
        return result;
    }

    private Map<String, Object> employeeScanPayload(Employee employee) {
        return employeeScanPayload(employee, employee.getBranch());
    }

    private Map<String, Object> employeeScanPayload(Employee employee, Branch branch) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", employee.getId().toString());
        map.put("name", employee.getFirstName() + " " + employee.getLastName());
        map.put("department", employee.getCategory() != null ? employee.getCategory().name() : "");
        map.put("position", employee.getCategory() != null ? employee.getCategory().name() : "");
        map.put("photoUrl", employee.getProfilePhotoUrl() != null ? employee.getProfilePhotoUrl() : "");
        map.put("branchCode", branch != null ? branch.getCode() : "");
        map.put("branchName", branch != null ? branch.getName() : "");
        map.put("preferredLanguage", employee.getPreferredLanguage() != null ? employee.getPreferredLanguage().name() : "EN");
        return map;
    }

    private Map<String, Object> toScanResult(AttendanceRecord record, String deviceId, boolean duplicate) {
        Employee employee = record.getEmployee();
        Branch branch = record.getBranch() != null ? record.getBranch() : employee.getBranch();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("duplicate", duplicate);
        result.put("recordId", record.getId().toString());
        result.put("scanType", toApiScanType(record.getScanType()));
        result.put("scannedAt", record.getScannedAt().toString());
        result.put("device", deviceId);
        result.put("employee", employeeScanPayload(employee, branch));
        enrichWithDayAttendance(result, employee, record);
        if (record.getScanType() == ScanType.ENTRY) {
            AttendanceEvaluationService.DayEvaluation eval = attendanceEvaluationService.evaluateDay(
                    employee, TimeUtils.toKigaliDate(record.getScannedAt()),
                    attendanceRecordRepository.findByEmployee_IdAndScannedAtBetweenOrderByScannedAtAsc(
                            employee.getId(),
                            TimeUtils.startOfDayKigali(TimeUtils.toKigaliDate(record.getScannedAt())),
                            TimeUtils.endOfDayKigali(TimeUtils.toKigaliDate(record.getScannedAt()))));
            if ("late".equals(eval.status())) {
                emailService.sendLateAlert(employee, employee.getBranch(), TimeUtils.toKigaliDate(record.getScannedAt()));
            }
        }
        return result;
    }

    private void enrichWithDayAttendance(Map<String, Object> result, Employee employee, AttendanceRecord record) {
        LocalDate day = TimeUtils.toKigaliDate(record.getScannedAt());
        List<AttendanceRecord> logs = attendanceRecordRepository
                .findByEmployee_IdAndScannedAtBetweenOrderByScannedAtAsc(
                        employee.getId(),
                        TimeUtils.startOfDayKigali(day),
                        TimeUtils.endOfDayKigali(day));

        AttendanceEvaluationService.DayEvaluation eval = attendanceEvaluationService.evaluateDay(employee, day, logs);
        WorkScheduleResolver.EffectiveSchedule schedule = workScheduleResolver.resolve(employee);

        Instant firstIn = null;
        Instant lastOut = null;
        for (AttendanceRecord log : logs) {
            if (log.getScanType() == ScanType.ENTRY) {
                if (firstIn == null || log.getScannedAt().isBefore(firstIn)) firstIn = log.getScannedAt();
            } else if (log.getScanType() == ScanType.EXIT) {
                if (lastOut == null || log.getScannedAt().isAfter(lastOut)) lastOut = log.getScannedAt();
            }
        }

        result.put("late", "late".equals(eval.status()));
        result.put("grace", eval.graceApplied());
        result.put("excused", eval.excused());
        result.put("minutesLate", eval.minutesLate());
        result.put("lostMinutes", eval.lostMinutes());
        result.put("earlyDeparture", "early_departure".equals(eval.status())
                || eval.earlyDepartureMinutes() > 0);
        result.put("dayComplete", eval.dayComplete());
        result.put("hoursWorked", eval.hoursWorked());
        result.put("firstIn", firstIn != null ? firstIn.toString() : null);
        result.put("lastOut", lastOut != null ? lastOut.toString() : null);
        result.put("expectedWorkStart", schedule.workStart().toString());
        result.put("expectedWorkEnd", schedule.workEnd().toString());
        result.put("attendanceStatus", eval.status());
        if (eval.earlyDepartureMinutes() > 0) {
            result.put("earlyDepartureMinutes", eval.earlyDepartureMinutes());
        }
    }

    private boolean isDuplicateSameTypeWindow(String cardUid, ScanType scanType, Instant scannedAt) {
        Instant since = scannedAt.minus(Duration.ofMinutes(attendanceProperties.getDuplicateScanWindowMinutes()));
        return !attendanceRecordRepository.findRecentByCardAndType(cardUid, scanType, since).isEmpty();
    }

    @Transactional
    public Map<String, Object> syncOffline(List<Map<String, Object>> records) {
        int synced = 0;
        int skipped = 0;
        int failed = 0;
        String deviceId = "unknown";

        List<Map<String, Object>> sorted = records == null ? List.of() : records.stream()
                .sorted(Comparator.comparing(r -> parseTimestamp(String.valueOf(r.get("timestamp")))))
                .toList();

        for (Map<String, Object> rec : sorted) {
            try {
                String uid = normalizeUid(String.valueOf(rec.get("uid")));
                String timestamp = String.valueOf(rec.get("timestamp"));
                deviceId = String.valueOf(rec.getOrDefault("deviceId", "unknown"));

                if (uid.isEmpty() || timestamp == null || "null".equals(timestamp)) {
                    failed++;
                    continue;
                }

                Employee employee = employeeRepository.findWithBranchByRfidCardUid(uid).orElse(null);
                if (employee == null || !employee.isEmploymentActive() || !employee.isRfidActive()) {
                    skipped++;
                    continue;
                }

                Instant scannedAt = parseTimestamp(timestamp);
                if (attendanceRecordRepository.existsByRfidCardUidAndScannedAt(uid, scannedAt)) {
                    skipped++;
                    continue;
                }

                RfidReader reader = resolveReader(deviceId, employee);
                ScanType scanType = resolveNextScanTypeAt(employee.getId(), scannedAt);

                List<AttendanceRecord> before = attendanceRecordRepository
                        .findByEmployee_IdAndScannedAtBetweenOrderByScannedAtAsc(
                                employee.getId(),
                                TimeUtils.startOfDayKigali(TimeUtils.toKigaliDate(scannedAt)),
                                scannedAt);
                AttendanceRecord lastBefore = before.isEmpty() ? null : before.get(before.size() - 1);
                if (lastBefore != null && isWithinToggleCooldown(lastBefore, scanType, scannedAt)) {
                    skipped++;
                    continue;
                }

                AttendanceRecord record = AttendanceRecord.builder()
                        .employee(employee)
                        .branch(employee.getBranch())
                        .rfidReader(reader)
                        .rfidCardUid(uid)
                        .scanType(scanType)
                        .scannedAt(scannedAt)
                        .synced(true)
                        .syncAt(Instant.now())
                        .syncSource(SyncSource.OFFLINE_SD)
                        .build();
                attendanceRecordRepository.save(record);
                synced++;
            } catch (Exception rowEx) {
                log.warn("syncOffline row error: {}", rowEx.getMessage());
                failed++;
            }
        }

        offlineSyncLogRepository.save(OfflineSyncLog.builder()
                .deviceId(deviceId)
                .recordsSent(sorted.size())
                .recordsOk(synced)
                .recordsSkip(skipped)
                .recordsFail(failed)
                .build());

        log.info("Offline sync: synced={} skipped={} failed={}", synced, skipped, failed);
        return Map.of(
                "synced", synced,
                "skipped", skipped,
                "failed", failed,
                "total", sorted.size()
        );
    }

    @Transactional
    public Map<String, Object> recordUnknownCard(String uid, String deviceId) {
        String normalized = normalizeUid(uid);
        Object lock = unknownUidLocks.computeIfAbsent(normalized, k -> new Object());
        synchronized (lock) {
            Instant now = Instant.now();
            long cooldownMs = attendanceProperties.getUnknownCardCooldownMinutes() * 60_000L;

            Optional<UnknownCard> existing = unknownCardRepository.findByUid(normalized);
            if (existing.isPresent()) {
                UnknownCard card = existing.get();
                Instant lastSeen = card.getLastSeen() != null ? card.getLastSeen() : card.getFirstSeen();
                if (lastSeen != null) {
                    long elapsedMs = Duration.between(lastSeen, now).toMillis();
                    if (elapsedMs < cooldownMs) {
                        long minutesRemaining = (cooldownMs - elapsedMs + 59_999) / 60_000;
                        return unknownCooldownResult(normalized, deviceId, card, minutesRemaining);
                    }
                }
                card.setScanCount(card.getScanCount() + 1);
                card.setDeviceId(deviceId);
                unknownCardRepository.save(card);
                return unknownAcceptedResult(card, false);
            }

            UnknownCard created = unknownCardRepository.save(UnknownCard.builder()
                    .uid(normalized)
                    .deviceId(deviceId)
                    .scanCount(1)
                    .build());
            return unknownAcceptedResult(created, false);
        }
    }

    private Map<String, Object> unknownAcceptedResult(UnknownCard card, boolean cooldown) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("cooldown", cooldown);
        result.put("duplicate", false);
        result.put("uid", card.getUid());
        result.put("deviceId", card.getDeviceId());
        result.put("scanCount", card.getScanCount());
        result.put("lastSeen", card.getLastSeen() != null ? card.getLastSeen().toString() : null);
        return result;
    }

    private Map<String, Object> unknownCooldownResult(
            String uid,
            String deviceId,
            UnknownCard card,
            long minutesRemaining
    ) {
        int cooldownMin = attendanceProperties.getUnknownCardCooldownMinutes();
        String message = """
                Cette carte (%s) n'est pas encore attribuée à un employé. \
                Veuillez attendre %d minute(s) avant de la scanner à nouveau, \
                ou assignez-la dans RFID Employees."""
                .formatted(uid, minutesRemaining > 0 ? minutesRemaining : 1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", false);
        result.put("cooldown", true);
        result.put("duplicate", true);
        result.put("error", "unknown_card_cooldown");
        result.put("message", message);
        result.put("uid", uid);
        result.put("deviceId", deviceId);
        result.put("scanCount", card.getScanCount());
        result.put("minutesRemaining", minutesRemaining);
        result.put("cooldownMinutes", cooldownMin);
        result.put("lastSeen", card.getLastSeen() != null ? card.getLastSeen().toString() : null);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUnknownCards() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UnknownCard card : unknownCardRepository.findAllByOrderByLastSeenDesc()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uid", card.getUid());
            row.put("deviceId", card.getDeviceId());
            row.put("scanCount", card.getScanCount());
            row.put("firstSeen", card.getFirstSeen() != null ? card.getFirstSeen().toString() : null);
            row.put("lastSeen", card.getLastSeen() != null ? card.getLastSeen().toString() : null);
            result.add(row);
        }
        return result;
    }

    @Transactional
    public void assignUnknownCard(String uid, String employeeIdRaw) {
        UUID employeeId = UUID.fromString(employeeIdRaw);
        Employee employee = employeeRepository.findWithBranchById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeIdRaw));

        String normalized = normalizeUid(uid);
        employeeRepository.findByRfidCardUid(normalized).ifPresent(other -> {
            if (!other.getId().equals(employeeId)) {
                throw new BusinessException("error.rfid.uid.taken");
            }
        });

        employee.setRfidCardUid(normalized);
        employee.setRfidAssignedAt(Instant.now());
        employee.setRfidActive(true);
        employeeRepository.save(employee);
        unknownCardRepository.findByUid(normalized).ifPresent(unknownCardRepository::delete);
        log.info("Assigned card {} to employee {}", normalized, employeeId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTodaySummary() {
        return getTodaySummary(null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTodaySummary(UUID branchIdFilter) {
        LocalDate today = LocalDate.now(TimeUtils.kigali());
        Instant dayStart = TimeUtils.startOfDayKigali(today);
        Instant dayEnd = TimeUtils.endOfDayKigali(today);

        List<Employee> activeEmployees = branchIdFilter != null
                ? employeeRepository.findAllActiveByBranch_Id(branchIdFilter)
                : employeeRepository.findAll().stream()
                        .filter(Employee::isEmploymentActive)
                        .toList();

        List<Map<String, Object>> employees = new ArrayList<>();
        int present = 0;
        int onSite = 0;
        int checkedOut = 0;
        int absent = 0;
        int earlyDepartures = 0;
        int lateCount = 0;
        int graceCount = 0;
        int excusedCount = 0;
        int totalCheckIns = 0;
        int totalCheckOuts = 0;

        Set<UUID> explanationSentToday = safeExplanationSentToday(dayStart, dayEnd);

        for (Employee emp : activeEmployees) {
            List<AttendanceRecord> todayLogs = attendanceRecordRepository
                    .findByEmployee_IdAndScannedAtBetweenOrderByScannedAtAsc(emp.getId(), dayStart, dayEnd);

            Instant firstIn = null;
            Instant lastOut = null;
            int employeeCheckIns = 0;
            int employeeCheckOuts = 0;
            ScanType lastScanType = null;
            Instant lastScanAt = null;
            Set<String> seenEvents = new HashSet<>();
            for (AttendanceRecord log : todayLogs) {
                String eventKey = log.getScanType() + "|" + log.getScannedAt().getEpochSecond();
                if (!seenEvents.add(eventKey)) {
                    continue;
                }
                if (log.getScanType() == ScanType.ENTRY) {
                    totalCheckIns++;
                    employeeCheckIns++;
                    if (firstIn == null || log.getScannedAt().isBefore(firstIn)) {
                        firstIn = log.getScannedAt();
                    }
                } else if (log.getScanType() == ScanType.EXIT) {
                    totalCheckOuts++;
                    employeeCheckOuts++;
                    if (lastOut == null || log.getScannedAt().isAfter(lastOut)) {
                        lastOut = log.getScannedAt();
                    }
                }
                if (lastScanAt == null || !log.getScannedAt().isBefore(lastScanAt)) {
                    lastScanAt = log.getScannedAt();
                    lastScanType = log.getScanType();
                }
            }

            AttendanceEvaluationService.DayEvaluation eval =
                    attendanceEvaluationService.evaluateDay(emp, today, todayLogs);
            String dailyStatus = computeDailyStatus(firstIn, eval);
            String currentState = computeCurrentState(firstIn, lastScanType, employeeCheckIns);
            boolean employeeOnSite = "on_site".equals(currentState) || "returned".equals(currentState);

            if (employeeOnSite) {
                onSite++;
                present++;
            } else if ("left_site".equals(currentState)) {
                checkedOut++;
                if (eval.earlyDepartureMinutes() > 0) {
                    earlyDepartures++;
                }
            }

            switch (dailyStatus) {
                case "absent" -> absent++;
                case "late" -> lateCount++;
                case "grace" -> graceCount++;
                case "excused" -> excusedCount++;
                case "off_day" -> { }
                default -> { }
            }

            WorkScheduleResolver.EffectiveSchedule schedule = workScheduleResolver.resolve(emp, today);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", emp.getId().toString());
            row.put("fullName", emp.getFirstName() + " " + emp.getLastName());
            row.put("department", emp.getCategory() != null ? emp.getCategory().name() : "");
            row.put("position", emp.getCategory() != null ? emp.getCategory().name() : "");
            row.put("photoUrl", emp.getProfilePhotoUrl());
            Branch branch = emp.getBranch();
            row.put("branchCode", branch != null ? branch.getCode() : "");
            row.put("branchName", branch != null ? branch.getName() : "");
            row.put("firstIn", firstIn != null ? firstIn.toString() : null);
            row.put("lastOut", "left_site".equals(currentState) && lastOut != null ? lastOut.toString() : null);
            row.put("lastScanAt", lastScanAt != null ? lastScanAt.toString() : null);
            row.put("lastScanType", lastScanType == ScanType.ENTRY ? "check_in" : lastScanType == ScanType.EXIT ? "check_out" : null);
            row.put("scanCount", todayLogs.size());
            row.put("totalCheckIns", employeeCheckIns);
            row.put("totalCheckOuts", employeeCheckOuts);
            row.put("dailyStatus", dailyStatus);
            row.put("currentState", currentState);
            row.put("attendanceStatus", dailyStatus);
            row.put("onSite", employeeOnSite);
            row.put("late", "late".equals(dailyStatus));
            row.put("isLate", "late".equals(dailyStatus));
            row.put("grace", eval.graceApplied());
            row.put("excused", eval.excused());
            row.put("minutesLate", eval.minutesLate());
            row.put("lostMinutes", eval.lostMinutes());
            row.put("earlyDeparture", eval.earlyDepartureMinutes() > 0 && "left_site".equals(currentState));
            row.put("dayComplete", eval.dayComplete());
            row.put("hoursWorked", eval.hoursWorked());
            row.put("explanationRequestedToday", explanationSentToday.contains(emp.getId()));
            row.put("expectedWorkStart", schedule.workStart().toString());
            row.put("expectedWorkEnd", schedule.workEnd().toString());
            if (eval.earlyDepartureMinutes() > 0 && lastOut != null) {
                row.put("earlyDepartureMinutes", eval.earlyDepartureMinutes());
            }
            employees.add(row);
        }

        employees.sort(Comparator
                .comparing((Map<String, Object> e) -> currentStateOrder(String.valueOf(e.get("currentState"))))
                .thenComparing(e -> dailyStatusOrder(String.valueOf(e.get("dailyStatus"))))
                .thenComparing(e -> String.valueOf(e.get("fullName"))));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("present", onSite);
        stats.put("onSite", onSite);
        stats.put("leftSite", checkedOut);
        stats.put("checkedOut", checkedOut);
        stats.put("earlyDepartures", earlyDepartures);
        stats.put("late", lateCount);
        stats.put("grace", graceCount);
        stats.put("excused", excusedCount);
        stats.put("absent", absent);
        stats.put("total", activeEmployees.size());
        stats.put("totalCheckIns", totalCheckIns);
        stats.put("totalCheckOuts", totalCheckOuts);

        return Map.of(
                "employees", employees,
                "stats", stats
        );
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> getLiveStats() {
        Map<String, Object> summary = getTodaySummary();
        Object stats = summary.get("stats");
        return stats instanceof Map ? (Map<String, Object>) stats : Map.of();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentActivity(int limit) {
        return getRecentActivity(limit, null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentActivity(int limit, UUID branchIdFilter) {
        int capped = Math.max(1, Math.min(limit, 50));
        LocalDate today = LocalDate.now(TimeUtils.kigali());
        Instant dayStart = TimeUtils.startOfDayKigali(today);
        Instant dayEnd = TimeUtils.endOfDayKigali(today);

        List<Map<String, Object>> items = new ArrayList<>();
        List<AttendanceRecord> records = attendanceRecordRepository.findRecentForDay(
                dayStart, dayEnd, PageRequest.of(0, capped));

        for (AttendanceRecord r : records) {
            Employee e = r.getEmployee();
            Branch branch = r.getBranch() != null ? r.getBranch() : e.getBranch();
            if (branchIdFilter != null && (branch == null || !branchIdFilter.equals(branch.getId()))) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kind", "scan");
            row.put("id", r.getId().toString());
            row.put("scannedAt", r.getScannedAt().toString());
            row.put("scanType", toApiScanType(r.getScanType()));
            row.put("device", r.getRfidReader() != null ? r.getRfidReader().getReaderCode() : "");
            row.put("branchCode", branch != null ? branch.getCode() : "");
            row.put("branchName", branch != null ? branch.getName() : "");
            row.put("employee", employeeScanPayload(e, branch));
            items.add(row);
        }

        for (UnknownCard card : unknownCardRepository.findAllByOrderByLastSeenDesc()) {
            if (items.size() >= capped) break;
            if (card.getLastSeen() == null) continue;
            if (card.getLastSeen().isBefore(dayStart) || !card.getLastSeen().isBefore(dayEnd)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kind", "unknown");
            row.put("id", "unknown:" + card.getUid());
            row.put("scannedAt", card.getLastSeen().toString());
            row.put("uid", card.getUid());
            row.put("deviceId", card.getDeviceId());
            items.add(row);
        }

        items.sort((a, b) -> String.valueOf(b.get("scannedAt")).compareTo(String.valueOf(a.get("scannedAt"))));
        if (items.size() > capped) {
            return new ArrayList<>(items.subList(0, capped));
        }
        return items;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTodayLatestScans() {
        return getTodayLatestScans(null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTodayLatestScans(UUID branchIdFilter) {
        LocalDate today = LocalDate.now(TimeUtils.kigali());
        Instant dayStart = TimeUtils.startOfDayKigali(today);
        Instant dayEnd = TimeUtils.endOfDayKigali(today);

        List<AttendanceRecord> records = attendanceRecordRepository.findRecentForDay(
                dayStart, dayEnd, PageRequest.of(0, 500));

        Map<UUID, List<AttendanceRecord>> byEmployee = new LinkedHashMap<>();
        for (AttendanceRecord record : records) {
            UUID empId = record.getEmployee().getId();
            byEmployee.computeIfAbsent(empId, k -> new ArrayList<>()).add(record);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (List<AttendanceRecord> logs : byEmployee.values()) {
            logs.sort(Comparator.comparing(AttendanceRecord::getScannedAt).reversed());
            AttendanceRecord latest = logs.get(0);
            Employee employee = latest.getEmployee();
            Branch branch = latest.getBranch() != null ? latest.getBranch() : employee.getBranch();
            if (branchIdFilter != null && (branch == null || !branchIdFilter.equals(branch.getId()))) {
                continue;
            }

            Instant firstIn = null;
            Instant lastOut = null;
            for (AttendanceRecord log : logs) {
                if (log.getScanType() == ScanType.ENTRY) {
                    if (firstIn == null || log.getScannedAt().isBefore(firstIn)) {
                        firstIn = log.getScannedAt();
                    }
                } else if (log.getScanType() == ScanType.EXIT) {
                    if (lastOut == null || log.getScannedAt().isAfter(lastOut)) {
                        lastOut = log.getScannedAt();
                    }
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", employee.getId().toString());
            row.put("fullName", employee.getFirstName() + " " + employee.getLastName());
            row.put("department", employee.getCategory() != null ? employee.getCategory().name() : "");
            row.put("position", employee.getCategory() != null ? employee.getCategory().name() : "");
            row.put("branchCode", branch != null ? branch.getCode() : "");
            row.put("branchName", branch != null ? branch.getName() : "");
            row.put("photoUrl", employee.getProfilePhotoUrl());
            row.put("scanType", toApiScanType(latest.getScanType()));
            row.put("scannedAt", latest.getScannedAt().toString());
            row.put("deviceId", latest.getRfidReader() != null ? latest.getRfidReader().getReaderCode() : "");
            row.put("syncedFrom", toApiSyncSource(latest.getSyncSource()));
            row.put("scanCount", logs.size());
            row.put("firstIn", firstIn != null ? firstIn.toString() : null);
            row.put("lastOut", lastOut != null ? lastOut.toString() : null);
            row.put("attendanceStatus", computeAttendanceStatusFromLogs(employee, today, logs));
            boolean dayComplete = firstIn != null && lastOut != null
                    && latest.getScanType() == ScanType.EXIT;
            row.put("dayComplete", dayComplete);
            if (firstIn != null && lastOut != null && lastOut.isAfter(firstIn)) {
                row.put("hoursWorked", Math.round(Duration.between(firstIn, lastOut).toMinutes() / 6.0) / 10.0);
            }
            row.put("late", workScheduleResolver.isLate(employee, firstIn));
            if (latest.getScanType() == ScanType.EXIT) {
                boolean early = workScheduleResolver.isEarlyDeparture(employee, latest.getScannedAt());
                row.put("earlyDeparture", early);
                if (early) {
                    row.put("earlyDepartureMinutes", workScheduleResolver.earlyDepartureMinutes(employee, latest.getScannedAt()));
                }
            }
            items.add(row);
        }

        items.sort((a, b) -> String.valueOf(b.get("scannedAt")).compareTo(String.valueOf(a.get("scannedAt"))));
        return items;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEmployeeDetail(UUID employeeId) {
        return getEmployeeDetail(employeeId, LocalDate.now(TimeUtils.kigali()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEmployeeDetail(UUID employeeId, LocalDate date) {
        Employee employee = employeeRepository.findWithBranchById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        LocalDate today = LocalDate.now(TimeUtils.kigali());
        boolean isToday = date.equals(today);
        Instant dayStart = TimeUtils.startOfDayKigali(date);
        Instant dayEnd = TimeUtils.endOfDayKigali(date);
        List<AttendanceRecord> todayLogs = attendanceRecordRepository
                .findByEmployee_IdAndScannedAtBetweenOrderByScannedAtAsc(employeeId, dayStart, dayEnd);

        ScanDayMetrics metrics = computeScanDayMetrics(todayLogs, employee);

        AttendanceEvaluationService.DayEvaluation todayEval =
                attendanceEvaluationService.evaluateDay(employee, date, todayLogs);

        Branch branch = employee.getBranch();
        Instant weekStart = TimeUtils.startOfDayKigali(today.minusDays(6));
        List<AttendanceRecord> weekLogs = attendanceRecordRepository
                .findByEmployee_IdAndScannedAtBetweenOrderByScannedAtAsc(employeeId, weekStart, dayEnd);
        int weekLostMinutes = 0;
        Map<LocalDate, List<AttendanceRecord>> weekByDay = new LinkedHashMap<>();
        for (AttendanceRecord record : weekLogs) {
            LocalDate d = TimeUtils.toKigaliDate(record.getScannedAt());
            weekByDay.computeIfAbsent(d, k -> new ArrayList<>()).add(record);
        }
        for (Map.Entry<LocalDate, List<AttendanceRecord>> entry : weekByDay.entrySet()) {
            AttendanceEvaluationService.DayEvaluation eval =
                    attendanceEvaluationService.evaluateDay(employee, entry.getKey(), entry.getValue());
            weekLostMinutes += eval.lostMinutes();
        }
        long daysPresent = weekByDay.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeId", employee.getId().toString());
        result.put("fullName", employee.getFirstName() + " " + employee.getLastName());
        result.put("email", employee.getEmail());
        result.put("department", employee.getCategory() != null ? employee.getCategory().name() : "");
        result.put("position", employee.getCategory() != null ? employee.getCategory().name() : "");
        result.put("branch", branch != null ? branch.getName() : "");
        result.put("branchCode", branch != null ? branch.getCode() : "");
        result.put("branchName", branch != null ? branch.getName() : "");
        result.put("photoUrl", employee.getProfilePhotoUrl());
        result.put("rfidUid", employee.getRfidCardUid());
        result.put("firstIn", metrics.firstIn != null ? metrics.firstIn.toString() : null);
        result.put("lastOut", metrics.lastOut != null ? metrics.lastOut.toString() : null);
        result.put("scanCount", todayLogs.size());
        result.put("checkOutCount", metrics.checkOutCount);
        result.put("returnCount", Math.max(0, metrics.checkInCount - 1));
        result.put("totalOnSiteMinutes", metrics.totalOnSiteMinutes);
        result.put("totalOnSiteFormatted", formatDurationMinutes(metrics.totalOnSiteMinutes));
        result.put("attendanceStatus", todayEval.status());
        result.put("dailyStatus", todayEval.status());
        result.put("minutesLate", todayEval.minutesLate());
        result.put("lostMinutes", todayEval.lostMinutes());
        result.put("weekLostMinutes", weekLostMinutes);
        result.put("todayLogs", metrics.logRows);
        result.put("daysPresent", daysPresent);
        result.put("totalScansWeek", weekLogs.size());
        result.put("explanationRequestedToday", isToday && safeHasExplanationToday(
                employeeId, TimeUtils.startOfDayKigali(today), TimeUtils.endOfDayKigali(today)));
        result.put("maxCheckoutsPerDay", attendanceProperties.getMaxCheckoutsPerDay());
        result.put("showExplanationButton", isToday && (
                "late".equals(todayEval.status())
                        || "absent".equals(todayEval.status())
                        || metrics.checkOutCount > attendanceProperties.getMaxCheckoutsPerDay()));
        result.put("viewDate", date.toString());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEmployeePeriodSummary(UUID employeeId, String period) {
        Employee employee = employeeRepository.findWithBranchById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        LocalDate today = LocalDate.now(TimeUtils.kigali());
        int lookbackDays = "month".equalsIgnoreCase(period) ? 29 : 6;
        Instant rangeStart = TimeUtils.startOfDayKigali(today.minusDays(lookbackDays));
        Instant rangeEnd = TimeUtils.endOfDayKigali(today);

        List<AttendanceRecord> records = attendanceRecordRepository
                .findByEmployee_IdAndScannedAtBetweenOrderByScannedAtAsc(employeeId, rangeStart, rangeEnd);

        Map<LocalDate, List<AttendanceRecord>> byDay = new LinkedHashMap<>();
        for (AttendanceRecord record : records) {
            LocalDate day = TimeUtils.toKigaliDate(record.getScannedAt());
            byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(record);
        }

        List<Map<String, Object>> days = new ArrayList<>();
        LocalDate cursor = today;
        while (!cursor.isBefore(today.minusDays(lookbackDays))) {
            List<AttendanceRecord> dayLogs = byDay.getOrDefault(cursor, List.of());
            dayLogs = new ArrayList<>(dayLogs);
            dayLogs.sort(Comparator.comparing(AttendanceRecord::getScannedAt));

            ScanDayMetrics metrics = computeScanDayMetrics(dayLogs);
            boolean stillOnSite = !dayLogs.isEmpty()
                    && dayLogs.get(dayLogs.size() - 1).getScanType() == ScanType.ENTRY;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", cursor.toString());
            row.put("firstIn", metrics.firstIn != null
                    ? DateTimeFormatter.ofPattern("HH:mm").withZone(TimeUtils.kigali()).format(metrics.firstIn)
                    : null);
            row.put("lastOut", !stillOnSite && metrics.lastOut != null
                    ? DateTimeFormatter.ofPattern("HH:mm").withZone(TimeUtils.kigali()).format(metrics.lastOut)
                    : null);
            row.put("stillOnSite", stillOnSite);
            row.put("duration", metrics.totalOnSiteMinutes > 0 ? formatDurationMinutes(metrics.totalOnSiteMinutes) : "—");
            row.put("totalOnSiteMinutes", metrics.totalOnSiteMinutes);
            row.put("checkInCount", metrics.checkInCount);
            row.put("checkOutCount", metrics.checkOutCount);
            row.put("scanCount", dayLogs.size());
            row.put("status", computeAttendanceStatusFromLogs(employee, cursor, dayLogs));
            days.add(row);
            cursor = cursor.minusDays(1);
        }

        return Map.of(
                "period", "month".equalsIgnoreCase(period) ? "month" : "week",
                "days", days
        );
    }

    private String computeAttendanceStatusFromLogs(Employee employee, LocalDate day, List<AttendanceRecord> logs) {
        return attendanceEvaluationService.evaluateDay(employee, day, logs).status();
    }

    private String toApiSyncSource(SyncSource source) {
        return source == SyncSource.OFFLINE_SD ? "offline_sd" : "online";
    }

    private int dailyStatusOrder(String status) {
        return switch (status) {
            case "present", "late", "grace" -> 0;
            case "checked_out" -> 1;
            case "early_departure" -> 2;
            case "off_day" -> 9;
            default -> 3;
        };
    }

    private int currentStateOrder(String state) {
        return switch (state) {
            case "on_site", "returned" -> 0;
            case "left_site" -> 1;
            default -> 2;
        };
    }

    /** Punctuality from first check-in only — unchanged after checkout. */
    private static String computeDailyStatus(Instant firstIn, AttendanceEvaluationService.DayEvaluation eval) {
        if (eval.excused()) {
            return "excused";
        }
        if (firstIn == null) {
            return eval.status();
        }
        if (eval.graceApplied()) {
            return "grace";
        }
        if (eval.minutesLate() > 0) {
            return "late";
        }
        return "present";
    }

    /** Real-time presence from the last scan of the day. */
    private static String computeCurrentState(Instant firstIn, ScanType lastScanType, int totalCheckIns) {
        if (firstIn == null) {
            return "absent";
        }
        if (lastScanType == null) {
            return "on_site";
        }
        if (lastScanType == ScanType.EXIT) {
            return "left_site";
        }
        return totalCheckIns > 1 ? "returned" : "on_site";
    }

    private Set<UUID> safeExplanationSentToday(Instant dayStart, Instant dayEnd) {
        try {
            return new HashSet<>(explanationRequestRepository.findEmployeeIdsWithRequestBetween(dayStart, dayEnd));
        } catch (Exception ex) {
            log.warn("Could not load explanation requests: {}", ex.getMessage());
            return Set.of();
        }
    }

    private boolean safeHasExplanationToday(UUID employeeId, Instant dayStart, Instant dayEnd) {
        try {
            return explanationRequestRepository.existsByEmployee_IdAndSentAtBetween(employeeId, dayStart, dayEnd);
        } catch (Exception ex) {
            log.warn("Could not check explanation request for {}: {}", employeeId, ex.getMessage());
            return false;
        }
    }

    private static String formatDurationMinutes(long minutes) {
        if (minutes <= 0) {
            return "—";
        }
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private ScanDayMetrics computeScanDayMetrics(List<AttendanceRecord> records) {
        return computeScanDayMetrics(records, null);
    }

    private ScanDayMetrics computeScanDayMetrics(List<AttendanceRecord> records, Employee employee) {
        ScanDayMetrics metrics = new ScanDayMetrics();
        Instant pendingIn = null;
        int pendingInLogIndex = -1;
        int index = 0;
        for (AttendanceRecord record : records) {
            index++;
            Branch branch = record.getBranch() != null ? record.getBranch()
                    : employee != null ? employee.getBranch() : null;
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("id", record.getId().toString());
            log.put("index", index);
            log.put("scanType", toApiScanType(record.getScanType()));
            log.put("scannedAt", record.getScannedAt().toString());
            log.put("deviceId", record.getRfidReader() != null ? record.getRfidReader().getReaderCode() : "");
            log.put("syncedFrom", toApiSyncSource(record.getSyncSource()));
            log.put("branch", branch != null ? branch.getName() : "");

            if (record.getScanType() == ScanType.ENTRY) {
                metrics.checkInCount++;
                if (metrics.firstIn == null || record.getScannedAt().isBefore(metrics.firstIn)) {
                    metrics.firstIn = record.getScannedAt();
                }
                pendingIn = record.getScannedAt();
                pendingInLogIndex = metrics.logRows.size();
                log.put("duration", null);
                log.put("durationMinutes", null);
                log.put("inProgress", false);
            } else if (record.getScanType() == ScanType.EXIT) {
                metrics.checkOutCount++;
                if (metrics.lastOut == null || record.getScannedAt().isAfter(metrics.lastOut)) {
                    metrics.lastOut = record.getScannedAt();
                }
                if (pendingIn != null && record.getScannedAt().isAfter(pendingIn)) {
                    long mins = Duration.between(pendingIn, record.getScannedAt()).toMinutes();
                    metrics.totalOnSiteMinutes += Math.max(0, mins);
                    log.put("durationMinutes", mins);
                    log.put("duration", formatDurationMinutes(mins));
                    log.put("inProgress", false);
                    pendingIn = null;
                } else {
                    log.put("duration", null);
                    log.put("durationMinutes", null);
                    log.put("inProgress", false);
                }
            }
            metrics.logRows.add(log);
        }
        if (pendingIn != null && pendingInLogIndex >= 0) {
            Map<String, Object> openLog = metrics.logRows.get(pendingInLogIndex);
            openLog.put("inProgress", true);
            openLog.put("duration", "in_progress");
        }
        return metrics;
    }

    private static final class ScanDayMetrics {
        Instant firstIn;
        Instant lastOut;
        int checkInCount;
        int checkOutCount;
        long totalOnSiteMinutes;
        final List<Map<String, Object>> logRows = new ArrayList<>();
    }

    private int statusOrder(String status) {
        return switch (status) {
            case "present", "late", "grace" -> 0;
            case "checked_out" -> 1;
            case "early_departure" -> 2;
            case "off_day" -> 9;
            default -> 3;
        };
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAnalyticsReport(
            String from,
            String to,
            String employeeIdRaw,
            String department,
            String branchIdRaw,
            String statusFilter,
            String groupBy,
            int page,
            int limit,
            String sortBy,
            String sortDir
    ) {
        LocalDate rawStart = (from != null && !from.isBlank()) ? LocalDate.parse(from) : LocalDate.now(TimeUtils.kigali());
        LocalDate rawEnd = (to != null && !to.isBlank()) ? LocalDate.parse(to) : rawStart;
        if (rawEnd.isBefore(rawStart)) {
            LocalDate tmp = rawStart;
            rawStart = rawEnd;
            rawEnd = tmp;
        }
        final LocalDate start = rawStart;
        final LocalDate end = rawEnd;

        int totalDays = (int) ChronoUnit.DAYS.between(start, end) + 1;
        Instant rangeStart = TimeUtils.startOfDayKigali(start);
        Instant rangeEnd = TimeUtils.endOfDayKigali(end);

        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(Employee::isEmploymentActive)
                .filter(e -> employeeIdRaw == null || employeeIdRaw.isBlank()
                        || e.getId().toString().equals(employeeIdRaw))
                .filter(e -> branchIdRaw == null || branchIdRaw.isBlank()
                        || (e.getBranch() != null && e.getBranch().getId().toString().equals(branchIdRaw)))
                .filter(e -> department == null || department.isBlank()
                        || (e.getCategory() != null && e.getCategory().name().equalsIgnoreCase(department)))
                .sorted(Comparator.comparing(e -> e.getFirstName() + " " + e.getLastName()))
                .toList();

        Map<String, List<AttendanceRecord>> recordsByEmployeeDay = new HashMap<>();
        for (AttendanceRecord record : attendanceRecordRepository.findAll()) {
            if (record.getScannedAt().isBefore(rangeStart) || !record.getScannedAt().isBefore(rangeEnd)) {
                continue;
            }
            if (employeeIdRaw != null && !employeeIdRaw.isBlank()
                    && !record.getEmployee().getId().toString().equals(employeeIdRaw)) {
                continue;
            }
            if (department != null && !department.isBlank()) {
                String cat = record.getEmployee().getCategory() != null
                        ? record.getEmployee().getCategory().name() : "";
                if (!cat.equalsIgnoreCase(department)) {
                    continue;
                }
            }
            if (branchIdRaw != null && !branchIdRaw.isBlank()) {
                Branch b = record.getEmployee().getBranch();
                if (b == null || !b.getId().toString().equals(branchIdRaw)) {
                    continue;
                }
            }
            String key = record.getEmployee().getId() + "|" + TimeUtils.toKigaliDate(record.getScannedAt());
            recordsByEmployeeDay.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }

        Set<String> departments = new HashSet<>();
        List<Map<String, Object>> allRows = new ArrayList<>();

        for (Employee employee : employees) {
            if (employee.getCategory() != null) {
                departments.add(employee.getCategory().name());
            }
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                String key = employee.getId() + "|" + day;
                List<AttendanceRecord> dayLogs = recordsByEmployeeDay.getOrDefault(key, List.of());
                List<AttendanceRecord> sortedLogs = new ArrayList<>(dayLogs);
                sortedLogs.sort(Comparator.comparing(AttendanceRecord::getScannedAt));

                Instant firstIn = null;
                Instant lastOut = null;
                ScanType lastType = null;
                for (AttendanceRecord log : sortedLogs) {
                    if (log.getScanType() == ScanType.ENTRY) {
                        if (firstIn == null || log.getScannedAt().isBefore(firstIn)) {
                            firstIn = log.getScannedAt();
                        }
                    } else if (log.getScanType() == ScanType.EXIT) {
                        if (lastOut == null || log.getScannedAt().isAfter(lastOut)) {
                            lastOut = log.getScannedAt();
                        }
                    }
                    lastType = log.getScanType();
                }

                AttendanceEvaluationService.DayEvaluation eval =
                        attendanceEvaluationService.evaluateDay(employee, day, dayLogs);
                String rowStatus = eval.status();

                if (statusFilter != null && !statusFilter.isBlank() && !"all".equalsIgnoreCase(statusFilter)) {
                    if (!rowStatus.equalsIgnoreCase(statusFilter)) {
                        continue;
                    }
                }

                Branch branch = employee.getBranch();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("employeeId", employee.getId().toString());
                row.put("fullName", employee.getFirstName() + " " + employee.getLastName());
                row.put("department", employee.getCategory() != null ? employee.getCategory().name() : "");
                row.put("branchName", branch != null ? branch.getName() : "");
                row.put("branchId", branch != null ? branch.getId().toString() : "");
                row.put("date", day.toString());
                row.put("checkIn", firstIn != null ? firstIn.toString() : null);
                row.put("checkOut", lastOut != null ? lastOut.toString() : null);
                row.put("hoursWorked", eval.hoursWorked());
                row.put("minutesLate", eval.minutesLate());
                row.put("lostMinutes", eval.lostMinutes());
                row.put("status", rowStatus);
                row.put("late", "late".equals(rowStatus));
                row.put("grace", eval.graceApplied());
                row.put("excused", eval.excused());
                row.put("photoUrl", employee.getProfilePhotoUrl());
                allRows.add(row);
            }
        }

        Map<String, Map<String, Integer>> dailyAgg = new LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            Map<String, Integer> bucket = new LinkedHashMap<>();
            bucket.put("present", 0);
            bucket.put("absent", 0);
            bucket.put("onTime", 0);
            bucket.put("late", 0);
            dailyAgg.put(day.toString(), bucket);
        }
        Map<UUID, ExpectedWorkDayService.OffDayIndex> offDayIndexes = new HashMap<>();
        for (Employee employee : employees) {
            UUID branchId = employee.getBranch() != null ? employee.getBranch().getId() : null;
            ExpectedWorkDayService.OffDayIndex offDayIndex = branchId != null
                    ? offDayIndexes.computeIfAbsent(branchId, bid -> expectedWorkDayService.buildIndex(bid, start, end))
                    : null;
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                String key = employee.getId() + "|" + day;
                List<AttendanceRecord> dayLogs = recordsByEmployeeDay.getOrDefault(key, List.of());
                Instant firstIn = dayLogs.stream()
                        .filter(l -> l.getScanType() == ScanType.ENTRY)
                        .map(AttendanceRecord::getScannedAt)
                        .min(Instant::compareTo)
                        .orElse(null);
                Map<String, Integer> bucket = dailyAgg.get(day.toString());
                if (offDayIndex != null && !offDayIndex.isExpectedWorkDay(day)) {
                    if (firstIn == null) {
                        continue;
                    }
                }
                if (firstIn == null) {
                    bucket.merge("absent", 1, Integer::sum);
                } else {
                    bucket.merge("present", 1, Integer::sum);
                    if (isLateCheckIn(employee, firstIn)) {
                        bucket.merge("late", 1, Integer::sum);
                    } else {
                        bucket.merge("onTime", 1, Integer::sum);
                    }
                }
            }
        }

        List<Map<String, Object>> dailySummary = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> entry : dailyAgg.entrySet()) {
            Map<String, Object> dayRow = new LinkedHashMap<>();
            dayRow.put("date", entry.getKey());
            dayRow.put("present", entry.getValue().get("present"));
            dayRow.put("absent", entry.getValue().get("absent"));
            dayRow.put("onTime", entry.getValue().get("onTime"));
            dayRow.put("late", entry.getValue().get("late"));
            dailySummary.add(dayRow);
        }

        Comparator<Map<String, Object>> comparator = switch (sortBy == null ? "date" : sortBy) {
            case "employee" -> Comparator.comparing(r -> String.valueOf(r.get("fullName")), String.CASE_INSENSITIVE_ORDER);
            case "department" -> Comparator.comparing(r -> String.valueOf(r.get("department")), String.CASE_INSENSITIVE_ORDER);
            case "status" -> Comparator.comparing(r -> String.valueOf(r.get("status")));
            case "hours" -> Comparator.comparing(r -> ((Number) r.get("hoursWorked")).doubleValue());
            case "minutesLate" -> Comparator.comparing(r -> ((Number) r.get("minutesLate")).intValue());
            case "lostMinutes" -> Comparator.comparing(r -> ((Number) r.get("lostMinutes")).intValue());
            default -> Comparator.comparing(r -> String.valueOf(r.get("date")));
        };
        if ("desc".equalsIgnoreCase(sortDir)) {
            comparator = comparator.reversed();
        }
        allRows.sort(comparator);

        long attendedDays = allRows.stream().filter(r -> !"absent".equals(r.get("status")) && !"excused".equals(r.get("status")) && !"off_day".equals(r.get("status"))).count();
        long lateDays = allRows.stream().filter(r -> "late".equals(r.get("status"))).count();
        long graceDays = allRows.stream().filter(r -> "grace".equals(r.get("status"))).count();
        long excusedDays = allRows.stream().filter(r -> "excused".equals(r.get("status"))).count();
        long absentDays = allRows.stream().filter(r -> "absent".equals(r.get("status"))).count();
        long onTimeDays = allRows.stream().filter(r -> {
            String s = String.valueOf(r.get("status"));
            return "present".equals(s) || "checked_out".equals(s);
        }).count();
        int lostMinutesTotal = allRows.stream().mapToInt(r -> ((Number) r.get("lostMinutes")).intValue()).sum();
        long totalEmployeeDays = (long) employees.size() * totalDays;
        double presentPercent = totalEmployeeDays == 0 ? 0 : (attendedDays * 100.0) / totalEmployeeDays;
        double latePercent = attendedDays == 0 ? 0 : (lateDays * 100.0) / attendedDays;
        double punctualityPercent = attendedDays == 0 ? 0 : (onTimeDays * 100.0) / attendedDays;
        double avgHours = allRows.stream()
                .map(r -> ((Number) r.get("hoursWorked")).doubleValue())
                .filter(h -> h > 0)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        avgHours = Math.round(avgHours * 100.0) / 100.0;

        String normalizedGroupBy = groupBy == null ? "overview" : groupBy.toLowerCase();
        List<Map<String, Object>> groups = List.of();
        if (!"overview".equals(normalizedGroupBy)) {
            groups = aggregateAnalyticsGroups(allRows, normalizedGroupBy);
            Comparator<Map<String, Object>> groupComparator = switch (sortBy == null ? "label" : sortBy) {
                case "present" -> Comparator.comparing(r -> ((Number) r.get("present")).intValue());
                case "absent" -> Comparator.comparing(r -> ((Number) r.get("absent")).intValue());
                case "late" -> Comparator.comparing(r -> ((Number) r.get("late")).intValue());
                case "grace" -> Comparator.comparing(r -> ((Number) r.get("grace")).intValue());
                case "excused" -> Comparator.comparing(r -> ((Number) r.get("excused")).intValue());
                case "minutesLate" -> Comparator.comparing(r -> ((Number) r.get("minutesLate")).intValue());
                case "lostMinutes" -> Comparator.comparing(r -> ((Number) r.get("lostMinutes")).intValue());
                case "presentPercent" -> Comparator.comparing(r -> ((Number) r.get("presentPercent")).doubleValue());
                case "punctualityPercent" -> Comparator.comparing(r -> ((Number) r.get("punctualityPercent")).doubleValue());
                default -> Comparator.comparing(r -> String.valueOf(r.get("groupLabel")), String.CASE_INSENSITIVE_ORDER);
            };
            if ("desc".equalsIgnoreCase(sortDir)) {
                groupComparator = groupComparator.reversed();
            }
            groups = new ArrayList<>(groups);
            groups.sort(groupComparator);
        }

        int safeLimit = Math.max(1, Math.min(limit, 10000));
        int offset = Math.max(0, (page - 1) * safeLimit);

        List<Map<String, Object>> pageRows;
        List<Map<String, Object>> pageGroups;
        int totalRows;
        int totalGroups;

        if ("overview".equals(normalizedGroupBy)) {
            int endIndex = Math.min(offset + safeLimit, allRows.size());
            pageRows = offset >= allRows.size() ? List.of() : allRows.subList(offset, endIndex);
            pageGroups = List.of();
            totalRows = allRows.size();
            totalGroups = 0;
        } else {
            pageRows = List.of();
            int endIndex = Math.min(offset + safeLimit, groups.size());
            pageGroups = offset >= groups.size() ? List.of() : groups.subList(offset, endIndex);
            totalRows = allRows.size();
            totalGroups = groups.size();
        }

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalDays", totalDays);
        kpis.put("presentPercent", Math.round(presentPercent * 10.0) / 10.0);
        kpis.put("latePercent", Math.round(latePercent * 10.0) / 10.0);
        kpis.put("punctualityPercent", Math.round(punctualityPercent * 10.0) / 10.0);
        kpis.put("averageHours", avgHours);
        kpis.put("graceDays", graceDays);
        kpis.put("excusedDays", excusedDays);
        kpis.put("absentDays", absentDays);
        kpis.put("lostMinutesTotal", lostMinutesTotal);
        kpis.put("lostHoursTotal", Math.round(lostMinutesTotal / 60.0 * 10.0) / 10.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", start.toString());
        result.put("to", end.toString());
        result.put("groupBy", normalizedGroupBy);
        result.put("kpis", kpis);
        result.put("dailySummary", dailySummary);
        result.put("departments", departments.stream().sorted().toList());
        result.put("rows", pageRows);
        result.put("groups", pageGroups);
        result.put("total", "overview".equals(normalizedGroupBy) ? totalRows : totalGroups);
        result.put("totalDetailRows", totalRows);
        result.put("page", page);
        result.put("limit", safeLimit);
        return result;
    }

    private List<Map<String, Object>> aggregateAnalyticsGroups(List<Map<String, Object>> allRows, String groupBy) {
        Map<String, List<Map<String, Object>>> buckets = new LinkedHashMap<>();
        for (Map<String, Object> row : allRows) {
            String key = switch (groupBy) {
                case "employee" -> String.valueOf(row.get("employeeId"));
                case "branch" -> String.valueOf(row.get("branchName"));
                case "role" -> String.valueOf(row.get("department"));
                default -> "all";
            };
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : buckets.entrySet()) {
            List<Map<String, Object>> rows = entry.getValue();
            if (rows.isEmpty()) {
                continue;
            }
            Map<String, Object> sample = rows.get(0);
            int present = 0;
            int absent = 0;
            int late = 0;
            int grace = 0;
            int excused = 0;
            int earlyDeparture = 0;
            int minutesLate = 0;
            int lostMinutes = 0;
            double hoursSum = 0;
            int hoursCount = 0;
            for (Map<String, Object> row : rows) {
                String status = String.valueOf(row.get("status"));
                switch (status) {
                    case "absent" -> absent++;
                    case "late" -> late++;
                    case "grace" -> grace++;
                    case "excused" -> excused++;
                    case "early_departure" -> earlyDeparture++;
                    default -> present++;
                }
                minutesLate += ((Number) row.get("minutesLate")).intValue();
                lostMinutes += ((Number) row.get("lostMinutes")).intValue();
                double h = ((Number) row.get("hoursWorked")).doubleValue();
                if (h > 0) {
                    hoursSum += h;
                    hoursCount++;
                }
            }
            int total = rows.size();
            long attended = total - absent - excused;
            double presentPercent = total == 0 ? 0 : (attended * 100.0) / total;
            long onTime = rows.stream().filter(r -> {
                String s = String.valueOf(r.get("status"));
                return "present".equals(s) || "checked_out".equals(s);
            }).count();
            double punctualityPercent = attended == 0 ? 0 : (onTime * 100.0) / attended;

            String groupLabel = switch (groupBy) {
                case "employee" -> String.valueOf(sample.get("fullName"));
                case "branch" -> String.valueOf(sample.get("branchName"));
                case "role" -> String.valueOf(sample.get("department"));
                default -> entry.getKey();
            };

            Map<String, Object> group = new LinkedHashMap<>();
            group.put("groupKey", entry.getKey());
            group.put("groupLabel", groupLabel.isBlank() ? "—" : groupLabel);
            group.put("employeeId", sample.get("employeeId"));
            group.put("fullName", sample.get("fullName"));
            group.put("branchName", sample.get("branchName"));
            group.put("department", sample.get("department"));
            group.put("totalDays", total);
            group.put("present", present);
            group.put("absent", absent);
            group.put("late", late);
            group.put("grace", grace);
            group.put("excused", excused);
            group.put("earlyDeparture", earlyDeparture);
            group.put("minutesLate", minutesLate);
            group.put("lostMinutes", lostMinutes);
            group.put("presentPercent", Math.round(presentPercent * 10.0) / 10.0);
            group.put("punctualityPercent", Math.round(punctualityPercent * 10.0) / 10.0);
            group.put("averageHours", hoursCount == 0 ? 0 : Math.round((hoursSum / hoursCount) * 100.0) / 100.0);
            groups.add(group);
        }
        return groups;
    }

    private boolean isLateCheckIn(Employee employee, Instant firstIn) {
        return workScheduleResolver.isLate(employee, firstIn);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEarlyDepartures(
            String from,
            String to,
            String branchIdRaw,
            UUID employeeIdFilter,
            int page,
            int limit
    ) {
        LocalDate rawStart = (from != null && !from.isBlank()) ? LocalDate.parse(from) : LocalDate.now(TimeUtils.kigali());
        LocalDate rawEnd = (to != null && !to.isBlank()) ? LocalDate.parse(to) : rawStart;
        if (rawEnd.isBefore(rawStart)) {
            LocalDate tmp = rawStart;
            rawStart = rawEnd;
            rawEnd = tmp;
        }
        final LocalDate start = rawStart;
        final LocalDate end = rawEnd;

        Instant rangeStart = TimeUtils.startOfDayKigali(start);
        Instant rangeEnd = TimeUtils.endOfDayKigali(end);

        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(Employee::isEmploymentActive)
                .filter(e -> employeeIdFilter == null || employeeIdFilter.equals(e.getId()))
                .filter(e -> branchIdRaw == null || branchIdRaw.isBlank()
                        || (e.getBranch() != null && e.getBranch().getId().toString().equals(branchIdRaw)))
                .toList();

        Map<String, List<AttendanceRecord>> recordsByEmployeeDay = new HashMap<>();
        for (AttendanceRecord record : attendanceRecordRepository.findAll()) {
            if (record.getScannedAt().isBefore(rangeStart) || !record.getScannedAt().isBefore(rangeEnd)) {
                continue;
            }
            if (branchIdRaw != null && !branchIdRaw.isBlank()) {
                UUID branchId = UUID.fromString(branchIdRaw);
                if (record.getEmployee().getBranch() == null
                        || !branchId.equals(record.getEmployee().getBranch().getId())) {
                    continue;
                }
            }
            String key = record.getEmployee().getId() + "|" + TimeUtils.toKigaliDate(record.getScannedAt());
            recordsByEmployeeDay.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<UUID, ExpectedWorkDayService.OffDayIndex> offDayIndexes = new HashMap<>();
        for (Employee employee : employees) {
            UUID branchId = employee.getBranch() != null ? employee.getBranch().getId() : null;
            ExpectedWorkDayService.OffDayIndex offDayIndex = branchId != null
                    ? offDayIndexes.computeIfAbsent(branchId, bid -> expectedWorkDayService.buildIndex(bid, start, end))
                    : null;
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                if (offDayIndex != null && !offDayIndex.isExpectedWorkDay(day)) {
                    continue;
                }
                String key = employee.getId() + "|" + day;
                List<AttendanceRecord> dayLogs = recordsByEmployeeDay.getOrDefault(key, List.of());
                if (dayLogs.isEmpty()) {
                    continue;
                }
                List<AttendanceRecord> sorted = new ArrayList<>(dayLogs);
                sorted.sort(Comparator.comparing(AttendanceRecord::getScannedAt));

                Instant firstIn = null;
                Instant lastOut = null;
                ScanType lastType = null;
                for (AttendanceRecord log : sorted) {
                    if (log.getScanType() == ScanType.ENTRY) {
                        if (firstIn == null || log.getScannedAt().isBefore(firstIn)) {
                            firstIn = log.getScannedAt();
                        }
                    } else if (log.getScanType() == ScanType.EXIT) {
                        if (lastOut == null || log.getScannedAt().isAfter(lastOut)) {
                            lastOut = log.getScannedAt();
                        }
                    }
                    lastType = log.getScanType();
                }
                if (firstIn == null || lastOut == null || lastType != ScanType.EXIT) {
                    continue;
                }
                if (!workScheduleResolver.isEarlyDeparture(employee, lastOut)) {
                    continue;
                }

                WorkScheduleResolver.EffectiveSchedule schedule = workScheduleResolver.resolve(employee, day);
                double hoursWorked = Math.round(Duration.between(firstIn, lastOut).toMinutes() / 6.0) / 10.0;
                Branch branch = employee.getBranch();

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("employeeId", employee.getId().toString());
                row.put("fullName", employee.getFirstName() + " " + employee.getLastName());
                row.put("department", employee.getCategory() != null ? employee.getCategory().name() : "");
                row.put("branchName", branch != null ? branch.getName() : "");
                row.put("branchCode", branch != null ? branch.getCode() : "");
                row.put("photoUrl", employee.getProfilePhotoUrl());
                row.put("date", day.toString());
                row.put("checkIn", firstIn.toString());
                row.put("checkOut", lastOut.toString());
                row.put("hoursWorked", hoursWorked);
                row.put("expectedWorkEnd", schedule.workEnd().toString());
                row.put("earlyDepartureMinutes", workScheduleResolver.earlyDepartureMinutes(employee, lastOut));
                row.put("late", workScheduleResolver.isLate(employee, firstIn));
                row.put("dayComplete", true);
                rows.add(row);
            }
        }

        rows.sort(Comparator
                .comparing((Map<String, Object> r) -> String.valueOf(r.get("date"))).reversed()
                .thenComparing(r -> String.valueOf(r.get("fullName"))));

        int safeLimit = Math.max(1, Math.min(limit, 200));
        int offset = Math.max(0, (page - 1) * safeLimit);
        int endIndex = Math.min(offset + safeLimit, rows.size());
        List<Map<String, Object>> pageRows = offset >= rows.size() ? List.of() : rows.subList(offset, endIndex);

        return Map.of(
                "from", start.toString(),
                "to", end.toString(),
                "rows", pageRows,
                "total", rows.size(),
                "page", page,
                "limit", safeLimit
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getReport(String from, String to, String employeeIdRaw,
                                         String department, int page, int limit) {
        List<Map<String, Object>> logs = new ArrayList<>();
        long total = 0;

        List<AttendanceRecord> all = attendanceRecordRepository.findAll();
        List<AttendanceRecord> filtered = all.stream()
                .filter(r -> {
                    LocalDate d = TimeUtils.toKigaliDate(r.getScannedAt());
                    if (from != null && !from.isBlank() && d.isBefore(LocalDate.parse(from))) return false;
                    if (to != null && !to.isBlank() && d.isAfter(LocalDate.parse(to))) return false;
                    if (employeeIdRaw != null && !employeeIdRaw.isBlank()
                            && !r.getEmployee().getId().toString().equals(employeeIdRaw)) return false;
                    if (department != null && !department.isBlank()) {
                        String cat = r.getEmployee().getCategory() != null
                                ? r.getEmployee().getCategory().name() : "";
                        if (!cat.equalsIgnoreCase(department)) return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparing(AttendanceRecord::getScannedAt).reversed())
                .toList();

        total = filtered.size();
        int offset = Math.max(0, (page - 1) * limit);
        int end = Math.min(offset + limit, filtered.size());

        for (AttendanceRecord r : filtered.subList(offset, end)) {
            Employee e = r.getEmployee();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId().toString());
            row.put("scanType", toApiScanType(r.getScanType()));
            row.put("scannedAt", r.getScannedAt().toString());
            row.put("deviceId", r.getRfidReader().getReaderCode());
            row.put("syncedFrom", r.getSyncSource() == SyncSource.OFFLINE_SD ? "offline_sd" : "online");
            row.put("employeeId", e.getId().toString());
            row.put("fullName", e.getFirstName() + " " + e.getLastName());
            row.put("department", e.getCategory() != null ? e.getCategory().name() : "");
            row.put("position", e.getCategory() != null ? e.getCategory().name() : "");
            row.put("photoUrl", e.getProfilePhotoUrl());
            logs.add(row);
        }

        return Map.of("logs", logs, "total", total, "page", page, "limit", limit);
    }

    @Transactional
    public String touchDeviceHeartbeat(String deviceId, String location, Integer freeHeap) {
        touchDevice(deviceId, Instant.now(), freeHeap);
        if (location != null && !location.isBlank()) {
            rfidDeviceRepository.findByDeviceId(deviceId).ifPresent(d -> {
                d.setLocation(location);
                rfidDeviceRepository.save(d);
            });
        }
        return rfidDeviceRepository.findByDeviceId(deviceId)
                .map(d -> d.getApprovalStatus().name().toLowerCase())
                .orElse("pending");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDevices() {
        return listDevicesByApproval(DeviceApprovalStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPendingDevices() {
        return listDevicesByApproval(DeviceApprovalStatus.PENDING);
    }

    private List<Map<String, Object>> listDevicesByApproval(DeviceApprovalStatus approvalStatus) {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> devices = new ArrayList<>();
        for (RfidDevice d : rfidDeviceRepository.findByApprovalStatusOrderByLastSeenDesc(approvalStatus)) {
            devices.add(toDeviceRow(d, now));
        }
        return devices;
    }

    private Map<String, Object> toDeviceRow(RfidDevice d, long now) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deviceId", d.getDeviceId());
        row.put("location", d.getLocation());
        row.put("description", d.getDescription());
        row.put("lastSeen", d.getLastSeen() != null ? d.getLastSeen().toString() : null);
        row.put("freeHeap", d.getFreeHeap());
        row.put("approvalStatus", d.getApprovalStatus().name().toLowerCase());
        boolean online = d.getLastSeen() != null && (now - d.getLastSeen().toEpochMilli()) < 120_000;
        row.put("status", online ? "online" : "offline");
        rfidReaderRepository.findWithBranchByReaderCode(d.getDeviceId()).ifPresent(reader -> {
            row.put("branchId", reader.getBranch().getId().toString());
            row.put("branchName", reader.getBranch().getName());
            row.put("readerType", reader.getReaderType().name());
        });
        return row;
    }

    @Transactional
    public Map<String, Object> approveDevice(
            String deviceId,
            UUID branchId,
            ReaderType readerType,
            String location,
            String description
    ) {
        RfidDevice device = rfidDeviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("error.device.not.found"));
        if (device.getApprovalStatus() != DeviceApprovalStatus.PENDING) {
            throw new BusinessException("error.device.not.pending");
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("error.branch.not.found"));
        ReaderType type = readerType != null ? readerType : ReaderType.ENTRY;
        if (location != null && !location.isBlank()) {
            device.setLocation(location.trim());
        }
        if (description != null && !description.isBlank()) {
            device.setDescription(description.trim());
        }
        device.setApprovalStatus(DeviceApprovalStatus.ACTIVE);
        rfidDeviceRepository.save(device);

        if (!rfidReaderRepository.existsByReaderCode(deviceId)) {
            rfidReaderRepository.save(RfidReader.builder()
                    .branch(branch)
                    .readerCode(deviceId)
                    .readerType(type)
                    .locationDescription(device.getLocation())
                    .online(device.getStatus() == DeviceStatus.online)
                    .lastSyncAt(device.getLastSeen())
                    .build());
        }
        log.info("Approved RFID device {} for branch {}", deviceId, branch.getCode());
        return Map.of("approved", true, "deviceId", deviceId);
    }

    @Transactional
    public void rejectDevice(String deviceId) {
        RfidDevice device = rfidDeviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("error.device.not.found"));
        if (device.getApprovalStatus() != DeviceApprovalStatus.PENDING) {
            throw new BusinessException("error.device.not.pending");
        }
        device.setApprovalStatus(DeviceApprovalStatus.REJECTED);
        rfidDeviceRepository.save(device);
        log.info("Rejected pending RFID device {}", deviceId);
    }

    @Transactional
    public void registerDevice(String deviceId, String location, String description) {
        if (rfidDeviceRepository.findByDeviceId(deviceId).isPresent()) {
            throw new BusinessException("error.device.exists");
        }
        rfidDeviceRepository.save(RfidDevice.builder()
                .deviceId(deviceId)
                .location(location)
                .description(description)
                .status(DeviceStatus.offline)
                .approvalStatus(DeviceApprovalStatus.ACTIVE)
                .build());
    }

    private RfidReader resolveReader(String deviceId, Employee employee) {
        return rfidReaderRepository.findWithBranchByReaderCode(deviceId)
                .orElseThrow(() -> new BusinessException("error.reader.not.found"));
    }

    private void touchDevice(String deviceId, Instant when, Integer freeHeap) {
        RfidDevice device = rfidDeviceRepository.findByDeviceId(deviceId).orElseGet(() ->
                RfidDevice.builder()
                        .deviceId(deviceId)
                        .status(DeviceStatus.online)
                        .approvalStatus(DeviceApprovalStatus.PENDING)
                        .build());
        device.setLastSeen(when);
        device.setStatus(DeviceStatus.online);
        if (freeHeap != null) {
            device.setFreeHeap(freeHeap);
        }
        rfidDeviceRepository.save(device);
    }

    private void touchReader(RfidReader reader) {
        reader.setOnline(true);
        reader.setLastSyncAt(Instant.now());
        reader.setPendingSyncCount(0);
        rfidReaderRepository.save(reader);
    }

    private ScanType resolveNextScanType(UUID employeeId, Instant scannedAt) {
        LocalDate day = TimeUtils.toKigaliDate(scannedAt);
        Instant dayStart = TimeUtils.startOfDayKigali(day);
        Instant dayEnd = TimeUtils.endOfDayKigali(day);
        List<AttendanceRecord> today = attendanceRecordRepository
                .findByEmployee_IdAndScannedAtBetweenOrderByScannedAtAsc(employeeId, dayStart, dayEnd);
        if (today.isEmpty()) {
            return ScanType.ENTRY;
        }
        AttendanceRecord last = today.get(today.size() - 1);
        return last.getScanType() == ScanType.ENTRY ? ScanType.EXIT : ScanType.ENTRY;
    }

    private ScanType resolveNextScanTypeAt(UUID employeeId, Instant scannedAt) {
        LocalDate day = TimeUtils.toKigaliDate(scannedAt);
        Instant dayStart = TimeUtils.startOfDayKigali(day);
        List<AttendanceRecord> before = attendanceRecordRepository
                .findByEmployee_IdAndScannedAtBetweenOrderByScannedAtAsc(employeeId, dayStart, scannedAt);
        if (before.isEmpty()) {
            return ScanType.ENTRY;
        }
        AttendanceRecord last = before.get(before.size() - 1);
        return last.getScanType() == ScanType.ENTRY ? ScanType.EXIT : ScanType.ENTRY;
    }

    private String normalizeUid(String uid) {
        return uid == null ? "" : uid.replace(":", "").replace(" ", "").trim().toUpperCase();
    }

    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(timestamp.trim());
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(timestamp.trim()).atZone(TimeUtils.kigali()).toInstant();
        }
    }

    private String toApiScanType(ScanType scanType) {
        return scanType == ScanType.ENTRY ? "check_in" : "check_out";
    }
}
