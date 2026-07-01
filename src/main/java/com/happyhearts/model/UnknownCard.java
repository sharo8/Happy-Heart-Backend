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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "unknown_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnknownCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String uid;

    @Column(name = "device_id", length = 50)
    private String deviceId;

    @Column(name = "scan_count", nullable = false)
    @Builder.Default
    private int scanCount = 1;

    @CreationTimestamp
    @Column(name = "first_seen", nullable = false, updatable = false)
    private Instant firstSeen;

    @UpdateTimestamp
    @Column(name = "last_seen")
    private Instant lastSeen;
}
