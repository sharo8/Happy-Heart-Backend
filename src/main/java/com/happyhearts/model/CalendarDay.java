package com.happyhearts.model;

import com.happyhearts.enums.CalendarEntryType;
import com.happyhearts.enums.CalendarEventSource;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per calendar date covered by a PDF-imported (or future materialized) school event.
 * Weekends are omitted so the grid can fall back to weekend styling for break spans.
 */
@Entity
@Table(
        name = "calendar_days",
        uniqueConstraints = @UniqueConstraint(name = "uk_calendar_days_event_date", columnNames = {"calendar_event_id", "day_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarDay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    @Column(name = "school_year", nullable = false, length = 9)
    private String schoolYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_kind", nullable = false, length = 20)
    private CalendarEntryType dayKind;

    @Column(name = "color_bg", length = 7)
    private String colorBg;

    @Column(name = "color_text", length = 7)
    private String colorText;

    @Column(nullable = false, length = 255)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CalendarEventSource source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_event_id", nullable = false)
    private CalendarEvent calendarEvent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
