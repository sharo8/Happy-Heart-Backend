package com.happyhearts.service;

import com.happyhearts.config.AttendanceProperties;
import com.happyhearts.config.RfidProperties;
import com.happyhearts.dto.request.Esp32BatchOfflineSyncRequest;
import com.happyhearts.dto.request.Esp32HeartbeatRequest;
import com.happyhearts.dto.request.Esp32OfflineRecordRequest;
import com.happyhearts.dto.request.Esp32OfflineSyncRequest;
import com.happyhearts.dto.request.Esp32RfidScanRequest;
import com.happyhearts.dto.request.OfflineSyncRecordRequest;
import com.happyhearts.dto.request.OfflineSyncRequest;
import com.happyhearts.dto.request.RfidScanRequest;
import com.happyhearts.dto.response.OfflineSyncResponse;
import com.happyhearts.dto.response.RfidScanResponse;
import com.happyhearts.enums.Role;
import com.happyhearts.enums.ScanType;
import com.happyhearts.enums.SyncSource;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.exception.RateLimitExceededException;
import com.happyhearts.exception.RfidCardInactiveException;
import com.happyhearts.exception.RfidCardNotFoundException;
import com.happyhearts.model.AttendanceRecord;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Employee;
import com.happyhearts.model.RfidReader;
import com.happyhearts.model.User;
import com.happyhearts.repository.AttendanceRecordRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.RfidReaderRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.util.TimeUtils;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RfidScanService {

    private final EmployeeRepository employeeRepository;
    private final RfidReaderRepository rfidReaderRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final UserRepository userRepository;
    private final AttendanceProperties attendanceProperties;
    private final RfidProperties rfidProperties;
    private final EmailService emailService;

    private final Map<String, Bucket> rateBuckets = new ConcurrentHashMap<>();

    @Transactional
    public RfidScanResponse scanFromEsp32(Esp32RfidScanRequest request) {
        RfidReader reader = rfidReaderRepository.findWithBranchByReaderCode(request.getDeviceId())
                .orElseThrow(() -> new BusinessException("error.reader.not.found"));

        String uid = normalizeUid(request.getUid());
        Employee employee = employeeRepository.findWithBranchByRfidCardUid(uid)
                .orElseThrow(RfidCardNotFoundException::new);
        if (!employee.isRfidActive()) {
            throw new RfidCardInactiveException();
        }
        if (!reader.getBranch().getId().equals(employee.getBranch().getId())) {
            throw new BusinessException("error.reader.branch.mismatch");
        }

        Instant scannedAt = parseDeviceTimestamp(request.getTimestamp());
        ScanType scanType = resolveNextScanType(employee.getId(), scannedAt);

        RfidScanRequest mapped = new RfidScanRequest();
        mapped.setRfidCardUid(uid);
        mapped.setRfidReaderId(reader.getId());
        mapped.setScanType(scanType);
        mapped.setScannedAt(scannedAt);

        RfidScanResponse response = scan(mapped, SyncSource.ONLINE, false);
        touchReader(reader, 0);
        return response;
    }

    @Transactional
    public OfflineSyncResponse syncOneFromEsp32(Esp32OfflineSyncRequest request) {
        Esp32BatchOfflineSyncRequest batch = new Esp32BatchOfflineSyncRequest();
        batch.setDeviceId(request.getDeviceId());
        Esp32OfflineRecordRequest record = new Esp32OfflineRecordRequest();
        record.setUid(request.getUid());
        record.setTimestamp(request.getTimestamp());
        batch.setRecords(List.of(record));
        return batchSyncFromEsp32(batch);
    }

    @Transactional
    public OfflineSyncResponse batchSyncFromEsp32(Esp32BatchOfflineSyncRequest request) {
        RfidReader reader = rfidReaderRepository.findWithBranchByReaderCode(request.getDeviceId())
                .orElseThrow(() -> new BusinessException("error.reader.not.found"));

        List<Esp32OfflineRecordRequest> sorted = request.getRecords().stream()
                .sorted(Comparator.comparing(r -> parseDeviceTimestamp(r.getTimestamp())))
                .toList();

        OfflineSyncRequest mapped = new OfflineSyncRequest();
        mapped.setRfidReaderId(reader.getId());
        mapped.setRecords(sorted.stream().map(rec -> {
            String uid = normalizeUid(rec.getUid());
            Instant scannedAt = parseDeviceTimestamp(rec.getTimestamp());
            Employee employee = employeeRepository.findWithBranchByRfidCardUid(uid).orElse(null);
            OfflineSyncRecordRequest out = new OfflineSyncRecordRequest();
            out.setRfidCardUid(uid);
            out.setScannedAt(scannedAt);
            if (employee != null) {
                out.setScanType(resolveNextScanType(employee.getId(), scannedAt));
            } else {
                out.setScanType(ScanType.ENTRY);
            }
            return out;
        }).toList());

        OfflineSyncResponse response = sync(mapped, SyncSource.OFFLINE_SD);
        touchReader(reader, 0);
        return response;
    }

    @Transactional
    public void heartbeatFromEsp32(Esp32HeartbeatRequest request) {
        RfidReader reader = rfidReaderRepository.findWithBranchByReaderCode(request.getDeviceId())
                .orElseThrow(() -> new BusinessException("error.reader.not.found"));
        touchReader(reader, request.getPendingSyncCount());
    }

    @Transactional
    public RfidScanResponse scan(RfidScanRequest request) {
        return scan(request, SyncSource.ONLINE, true);
    }

    private RfidScanResponse scan(RfidScanRequest request, SyncSource syncSource, boolean enforceReaderType) {
        String readerKey = request.getRfidReaderId().toString();
        Bucket bucket = rateBuckets.computeIfAbsent(readerKey, id -> Bucket.builder()
                .addLimit(Bandwidth.simple(rfidProperties.getScanRatePerSecond(), Duration.ofSeconds(1)))
                .build());
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException();
        }

        Employee employee = employeeRepository.findWithBranchByRfidCardUid(request.getRfidCardUid())
                .orElseThrow(RfidCardNotFoundException::new);
        if (!employee.isRfidActive()) {
            throw new RfidCardInactiveException();
        }

        RfidReader reader = rfidReaderRepository.findWithBranchById(request.getRfidReaderId())
                .orElseThrow(() -> new BusinessException("error.reader.not.found"));
        if (!reader.getBranch().getId().equals(employee.getBranch().getId())) {
            throw new BusinessException("error.reader.branch.mismatch");
        }
        if (enforceReaderType && !reader.getReaderType().name().equals(request.getScanType().name())) {
            throw new BusinessException("error.scan.type.mismatch");
        }

        Instant scannedAt = request.getScannedAt() != null ? request.getScannedAt() : Instant.now();
        if (isDuplicateShortWindow(request.getRfidCardUid(), request.getScanType(), scannedAt)) {
            return RfidScanResponse.builder()
                    .success(true)
                    .employeeName(employee.getFirstName() + " " + employee.getLastName())
                    .scanType(request.getScanType())
                    .scannedAt(scannedAt)
                    .build();
        }

        AttendanceRecord record = AttendanceRecord.builder()
                .employee(employee)
                .branch(reader.getBranch())
                .rfidReader(reader)
                .rfidCardUid(request.getRfidCardUid())
                .scanType(request.getScanType())
                .scannedAt(scannedAt)
                .synced(syncSource == SyncSource.OFFLINE_SD)
                .syncAt(syncSource == SyncSource.OFFLINE_SD ? Instant.now() : null)
                .syncSource(syncSource)
                .build();
        attendanceRecordRepository.save(record);

        if (request.getScanType() == ScanType.ENTRY && isLateEntry(employee.getBranch(), scannedAt)) {
            LocalDate day = TimeUtils.toKigaliDate(scannedAt);
            emailService.sendLateAlert(employee, employee.getBranch(), day);
            notifyBranchManagersLate(employee, day);
        }

        return RfidScanResponse.builder()
                .success(true)
                .employeeName(employee.getFirstName() + " " + employee.getLastName())
                .scanType(request.getScanType())
                .scannedAt(scannedAt)
                .build();
    }

    private void notifyBranchManagersLate(Employee employee, LocalDate day) {
        List<User> managers = userRepository.findByBranch_IdAndRole(employee.getBranch().getId(), Role.CENTRAL_COORDINATOR);
        Branch branch = employee.getBranch();
        for (User manager : managers) {
            emailService.sendLateAlertToManager(manager, employee, branch, day);
        }
    }

    @Transactional
    public OfflineSyncResponse sync(OfflineSyncRequest request) {
        return sync(request, SyncSource.OFFLINE_SD);
    }

    private OfflineSyncResponse sync(OfflineSyncRequest request, SyncSource syncSource) {
        RfidReader reader = rfidReaderRepository.findWithBranchById(request.getRfidReaderId())
                .orElseThrow(() -> new BusinessException("error.reader.not.found"));

        List<OfflineSyncRecordRequest> sorted = request.getRecords().stream()
                .sorted(Comparator.comparing(OfflineSyncRecordRequest::getScannedAt))
                .toList();

        int processed = 0;
        int skipped = 0;
        for (OfflineSyncRecordRequest rec : sorted) {
            Employee employee = employeeRepository.findWithBranchByRfidCardUid(rec.getRfidCardUid()).orElse(null);
            if (employee == null || !employee.isRfidActive()) {
                skipped++;
                continue;
            }
            if (!employee.getBranch().getId().equals(reader.getBranch().getId())) {
                skipped++;
                continue;
            }

            ScanType scanType = rec.getScanType() != null
                    ? rec.getScanType()
                    : resolveNextScanType(employee.getId(), rec.getScannedAt());

            Instant scannedAt = rec.getScannedAt();
            if (attendanceRecordRepository.existsByRfidCardUidAndScannedAt(rec.getRfidCardUid(), scannedAt)) {
                skipped++;
                continue;
            }
            Instant minuteStart = scannedAt.truncatedTo(ChronoUnit.MINUTES);
            Instant minuteEnd = minuteStart.plus(1, ChronoUnit.MINUTES);
            if (attendanceRecordRepository.findDuplicateInMinuteWindow(
                    rec.getRfidCardUid(), scanType, minuteStart, minuteEnd).isPresent()) {
                skipped++;
                continue;
            }
            if (isDuplicateShortWindow(rec.getRfidCardUid(), scanType, scannedAt)) {
                skipped++;
                continue;
            }

            AttendanceRecord record = AttendanceRecord.builder()
                    .employee(employee)
                    .branch(reader.getBranch())
                    .rfidReader(reader)
                    .rfidCardUid(rec.getRfidCardUid())
                    .scanType(scanType)
                    .scannedAt(scannedAt)
                    .synced(true)
                    .syncAt(Instant.now())
                    .syncSource(syncSource)
                    .build();
            attendanceRecordRepository.save(record);
            processed++;

            if (scanType == ScanType.ENTRY && isLateEntry(reader.getBranch(), scannedAt)) {
                LocalDate day = TimeUtils.toKigaliDate(scannedAt);
                emailService.sendLateAlert(employee, reader.getBranch(), day);
                notifyBranchManagersLate(employee, day);
            }
        }
        return OfflineSyncResponse.builder().processed(processed).skippedDuplicates(skipped).build();
    }

    private ScanType resolveNextScanType(UUID employeeId, Instant scannedAt) {
        LocalDate day = TimeUtils.toKigaliDate(scannedAt);
        Instant dayStart = day.atStartOfDay(TimeUtils.kigali()).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(TimeUtils.kigali()).toInstant();
        List<AttendanceRecord> today = attendanceRecordRepository.findTodayByEmployee(employeeId, dayStart, dayEnd);
        if (today.isEmpty()) {
            return ScanType.ENTRY;
        }
        return today.get(0).getScanType() == ScanType.ENTRY ? ScanType.EXIT : ScanType.ENTRY;
    }

    private void touchReader(RfidReader reader, int pendingSyncCount) {
        reader.setOnline(true);
        reader.setLastSyncAt(Instant.now());
        reader.setPendingSyncCount(Math.max(0, pendingSyncCount));
        rfidReaderRepository.save(reader);
    }

    private String normalizeUid(String uid) {
        return uid == null ? "" : uid.replace(":", "").replace(" ", "").trim().toUpperCase();
    }

    private Instant parseDeviceTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }
        String value = timestamp.trim();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(value).atZone(TimeUtils.kigali()).toInstant();
        }
    }

    private boolean isDuplicateShortWindow(String cardUid, ScanType scanType, Instant scannedAt) {
        Instant since = scannedAt.minus(Duration.ofMinutes(attendanceProperties.getDuplicateScanWindowMinutes()));
        List<AttendanceRecord> recent = attendanceRecordRepository.findRecentByCardAndType(cardUid, scanType, since);
        return !recent.isEmpty();
    }

    private boolean isLateEntry(Branch branch, Instant scannedAt) {
        ZonedDateTime z = scannedAt.atZone(TimeUtils.kigali());
        LocalTime arrival = z.toLocalTime();
        LocalTime start = branch.getWorkStartTime() != null
                ? branch.getWorkStartTime()
                : attendanceProperties.getWorkStartTime();
        int grace = branch.getGracePeriodMinutes() > 0
                ? branch.getGracePeriodMinutes()
                : attendanceProperties.getGracePeriodMinutes();
        LocalTime threshold = start.plusMinutes(grace);
        return arrival.isAfter(threshold);
    }
}
