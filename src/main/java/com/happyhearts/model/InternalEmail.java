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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "internal_emails")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @Column(name = "cc_user_ids", columnDefinition = "TEXT")
    private String ccUserIds;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String folder = "inbox";

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean starred = false;

    @Column(length = 50)
    private String label;

    @Column(name = "thread_id")
    private UUID threadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    private InternalEmail replyTo;

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "smtp_status", length = 20)
    private String smtpStatus;

    /** Real SMTP address used for delivery (employee HR email when set). */
    @Column(name = "delivery_email", length = 255)
    private String deliveryEmail;

    @Column(name = "smtp_provider", length = 20)
    private String smtpProvider;

    @Column(name = "outbound_subject", length = 500)
    private String outboundSubject;

    @Column(name = "email_notification_id")
    private UUID emailNotificationId;

    @Column(name = "smtp_error", columnDefinition = "TEXT")
    private String smtpError;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
