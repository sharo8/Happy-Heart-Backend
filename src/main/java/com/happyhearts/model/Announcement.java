package com.happyhearts.model;

import com.happyhearts.enums.AnnouncementAudienceType;
import com.happyhearts.enums.AnnouncementPriority;
import com.happyhearts.enums.AnnouncementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "send_to_all", nullable = false)
    @Builder.Default
    private boolean sendToAll = false;

    /** Comma-separated Role names when targeting by role */
    @Column(name = "target_roles", length = 200)
    private String targetRoles;

    @Column(name = "created_by_email", length = 255)
    private String createdByEmail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    @Builder.Default
    private AnnouncementPriority priority = AnnouncementPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false, length = 30)
    @Builder.Default
    private AnnouncementAudienceType audienceType = AnnouncementAudienceType.EVERYONE;

    /** Comma-separated UUIDs for branch targeting */
    @Column(name = "target_branch_ids", columnDefinition = "TEXT")
    private String targetBranchIds;

    /** Comma-separated EmployeeCategory names */
    @Column(name = "target_categories", columnDefinition = "TEXT")
    private String targetCategories;

    @Column(name = "send_immediately", nullable = false)
    @Builder.Default
    private boolean sendImmediately = true;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "email_notification", nullable = false)
    @Builder.Default
    private boolean emailNotification = true;

    @Column(name = "email_subject", length = 500)
    private String emailSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AnnouncementStatus status = AnnouncementStatus.SENT;

    @Column(name = "email_sent", nullable = false)
    @Builder.Default
    private boolean emailSent = false;

    @ManyToOne
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "recipient_count", nullable = false)
    @Builder.Default
    private int recipientCount = 0;
}
