package com.happyhearts.model;

import com.happyhearts.enums.CalendarEventSource;
import com.happyhearts.enums.EventType;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "calendar_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(name = "color_bg", length = 7)
    private String colorBg;

    @Column(name = "color_text", length = 7)
    private String colorText;

    @Column(name = "is_tentative", nullable = false)
    private boolean tentative;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "school_year", nullable = false, length = 9)
    private String schoolYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    private SchoolPeriod period;

    @Column(name = "period_name", length = 50)
    private String periodName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "apply_to_all_branches", nullable = false)
    private boolean applyToAllBranches;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CalendarEventSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pdf_import_id")
    private PdfImport pdfImport;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
