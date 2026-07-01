package com.happyhearts.model;

import com.happyhearts.enums.ScanType;
import com.happyhearts.enums.SyncSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfid_reader_id", nullable = false)
    private RfidReader rfidReader;

    @Column(name = "rfid_card_uid", nullable = false, length = 100)
    private String rfidCardUid;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", nullable = false, length = 20)
    private ScanType scanType;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @Column(name = "is_synced", nullable = false)
    private boolean synced;

    @Column(name = "sync_at")
    private Instant syncAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_source", length = 20)
    private SyncSource syncSource;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
