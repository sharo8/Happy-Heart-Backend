package com.happyhearts.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    private String location;

    private Double latitude;

    private Double longitude;

    @Column(name = "work_start_time", nullable = false)
    private LocalTime workStartTime;

    @Column(name = "grace_period_minutes", nullable = false)
    private Integer gracePeriodMinutes;

    @Column(name = "work_end_time")
    private LocalTime workEndTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_teacher_id")
    private Employee leadTeacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "second_teacher_id")
    private Employee secondTeacher;

    @Column(name = "created_by_email", length = 255)
    private String createdByEmail;

    @Column(name = "updated_by_email", length = 255)
    private String updatedByEmail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
