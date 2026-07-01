package com.happyhearts.model;

import com.happyhearts.enums.PdfImportStatus;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pdf_imports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfImport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(name = "school_year", nullable = false, length = 9)
    private String schoolYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_by")
    private User importedBy;

    @Column(name = "apply_to_all_branches", nullable = false)
    private boolean applyToAllBranches;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PdfImportStatus status;

    @Column(name = "events_created", nullable = false)
    private int eventsCreated;

    @Column(name = "periods_created", nullable = false)
    private int periodsCreated;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "parsed_data", columnDefinition = "json")
    private String parsedDataJson;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
