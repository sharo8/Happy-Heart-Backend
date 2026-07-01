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
import java.util.UUID;

@Entity
@Table(name = "gm_permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GmPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "page_key", nullable = false, length = 100)
    private String pageKey;

    @Column(name = "can_view", nullable = false)
    @Builder.Default
    private boolean canView = true;

    @Column(name = "can_create", nullable = false)
    @Builder.Default
    private boolean canCreate = false;

    @Column(name = "can_update", nullable = false)
    @Builder.Default
    private boolean canUpdate = false;

    @Column(name = "can_delete", nullable = false)
    @Builder.Default
    private boolean canDelete = false;

    @Column(name = "can_export", nullable = false)
    @Builder.Default
    private boolean canExport = false;

    @Column(name = "is_locked_full", nullable = false)
    @Builder.Default
    private boolean lockedFull = false;

    @Column(name = "is_locked_view_only", nullable = false)
    @Builder.Default
    private boolean lockedViewOnly = false;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
