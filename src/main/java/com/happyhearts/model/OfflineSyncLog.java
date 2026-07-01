package com.happyhearts.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "offline_sync_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfflineSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", length = 50)
    private String deviceId;

    @Column(name = "records_sent")
    private int recordsSent;

    @Column(name = "records_ok")
    private int recordsOk;

    @Column(name = "records_skip")
    private int recordsSkip;

    @Column(name = "records_fail")
    private int recordsFail;

    @CreationTimestamp
    @Column(name = "synced_at", nullable = false, updatable = false)
    private Instant syncedAt;
}
