package com.happyhearts.model;

import com.happyhearts.enums.Language;
import com.happyhearts.enums.Role;
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
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 5)
    private Language preferredLanguage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "password_change_required", nullable = false)
    @Builder.Default
    private boolean passwordChangeRequired = false;

    @Column(name = "initial_setup_token", unique = true, length = 128)
    private String initialSetupToken;

    @Column(name = "initial_setup_token_expires_at")
    private Instant initialSetupTokenExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Optional: HTTPS URL or data:image/...;base64 (same convention as employees). */
    @Column(name = "profile_photo_url", columnDefinition = "MEDIUMTEXT")
    private String profilePhotoUrl;
}
