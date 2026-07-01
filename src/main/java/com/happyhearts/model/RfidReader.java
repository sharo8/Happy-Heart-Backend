package com.happyhearts.model;

import com.happyhearts.enums.ReaderType;
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
@Table(name = "rfid_readers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfidReader {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "reader_code", nullable = false, unique = true, length = 100)
    private String readerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "reader_type", nullable = false, length = 20)
    private ReaderType readerType;

    @Column(name = "location_description")
    private String locationDescription;

    @Column(name = "is_online", nullable = false)
    private boolean online;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "pending_sync_count", nullable = false)
    @Builder.Default
    private int pendingSyncCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
