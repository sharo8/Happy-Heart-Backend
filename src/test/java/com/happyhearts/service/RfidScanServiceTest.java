package com.happyhearts.service;

import com.happyhearts.config.AttendanceProperties;
import com.happyhearts.config.RfidProperties;
import com.happyhearts.dto.request.RfidScanRequest;
import com.happyhearts.enums.ReaderType;
import com.happyhearts.enums.ScanType;
import com.happyhearts.exception.RfidCardInactiveException;
import com.happyhearts.exception.RfidCardNotFoundException;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Employee;
import com.happyhearts.model.RfidReader;
import com.happyhearts.repository.AttendanceRecordRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.repository.RfidReaderRepository;
import com.happyhearts.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RfidScanServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private RfidReaderRepository rfidReaderRepository;
    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;

    private RfidScanService rfidScanService;

    @BeforeEach
    void setUp() {
        AttendanceProperties attendanceProperties = new AttendanceProperties();
        attendanceProperties.setDuplicateScanWindowMinutes(5);
        attendanceProperties.setWorkStartTime(LocalTime.of(7, 30));
        attendanceProperties.setGracePeriodMinutes(15);
        RfidProperties rfidProperties = new RfidProperties();
        rfidProperties.setScanRatePerSecond(100);
        rfidScanService = new RfidScanService(
                employeeRepository,
                rfidReaderRepository,
                attendanceRecordRepository,
                userRepository,
                attendanceProperties,
                rfidProperties,
                emailService
        );
    }

    @Test
    void scan_throwsWhenCardUnknown() {
        when(employeeRepository.findWithBranchByRfidCardUid("X")).thenReturn(Optional.empty());
        RfidScanRequest req = new RfidScanRequest();
        req.setRfidCardUid("X");
        req.setRfidReaderId(UUID.randomUUID());
        req.setScannedAt(Instant.parse("2026-04-13T06:00:00Z"));
        req.setScanType(ScanType.ENTRY);
        assertThrows(RfidCardNotFoundException.class, () -> rfidScanService.scan(req));
    }

    @Test
    void scan_throwsWhenCardInactive() {
        Branch branch = Branch.builder().id(UUID.randomUUID()).build();
        Employee employee = Employee.builder().branch(branch).rfidActive(false).build();
        when(employeeRepository.findWithBranchByRfidCardUid("C")).thenReturn(Optional.of(employee));
        RfidScanRequest req = new RfidScanRequest();
        req.setRfidCardUid("C");
        req.setRfidReaderId(UUID.randomUUID());
        req.setScannedAt(Instant.now());
        req.setScanType(ScanType.ENTRY);
        assertThrows(RfidCardInactiveException.class, () -> rfidScanService.scan(req));
    }

    @Test
    void scan_savesWhenValid() {
        UUID branchId = UUID.randomUUID();
        Branch branch = Branch.builder()
                .id(branchId)
                .workStartTime(LocalTime.of(7, 30))
                .gracePeriodMinutes(15)
                .build();
        Employee employee = Employee.builder()
                .branch(branch)
                .firstName("A")
                .lastName("B")
                .rfidActive(true)
                .build();
        UUID readerId = UUID.randomUUID();
        RfidReader reader = RfidReader.builder()
                .id(readerId)
                .branch(branch)
                .readerType(ReaderType.ENTRY)
                .build();
        when(employeeRepository.findWithBranchByRfidCardUid("K")).thenReturn(Optional.of(employee));
        when(rfidReaderRepository.findWithBranchById(readerId)).thenReturn(Optional.of(reader));
        when(attendanceRecordRepository.findRecentByCardAndType(eq("K"), eq(ScanType.ENTRY), any()))
                .thenReturn(List.of());

        RfidScanRequest req = new RfidScanRequest();
        req.setRfidCardUid("K");
        req.setRfidReaderId(readerId);
        req.setScannedAt(Instant.parse("2026-04-13T06:00:00Z"));
        req.setScanType(ScanType.ENTRY);

        var res = rfidScanService.scan(req);
        assertEquals(true, res.isSuccess());
        verify(attendanceRecordRepository).save(any());
        verify(emailService, never()).sendLateAlert(any(), any(), any());
    }
}
