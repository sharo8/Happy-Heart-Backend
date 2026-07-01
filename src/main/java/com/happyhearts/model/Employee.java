package com.happyhearts.model;

import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.enums.Language;
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
import jakarta.persistence.OneToOne;
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
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String email;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EmployeeCategory category;

    @Column(name = "rfid_card_uid", unique = true, length = 100)
    private String rfidCardUid;

    @Column(name = "rfid_assigned_at")
    private Instant rfidAssignedAt;

    @Column(name = "rfid_is_active", nullable = false)
    @Builder.Default
    private boolean rfidActive = true;

    /** HR / portal: false = employment suspended (distinct from RFID card flag). */
    @Column(name = "employment_active", nullable = false)
    @Builder.Default
    private boolean employmentActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 5)
    private Language preferredLanguage;

    /** HTTPS URL or inline data URL (e.g. local image upload). */
    @Column(name = "profile_photo_url", columnDefinition = "MEDIUMTEXT")
    private String profilePhotoUrl;

    /** When true, branch work hours apply; otherwise employee fields below (with branch fallbacks). */
    @Column(name = "use_branch_schedule", nullable = false)
    @Builder.Default
    private boolean useBranchSchedule = true;

    @Column(name = "work_start_time")
    private LocalTime workStartTime;

    @Column(name = "work_end_time")
    private LocalTime workEndTime;

    @Column(name = "grace_period_minutes")
    private Integer gracePeriodMinutes;

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
