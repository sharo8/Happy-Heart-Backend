package com.happyhearts.model;

import com.happyhearts.enums.CalendarEntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "calendar_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private CalendarEntryType type;

    @Column(name = "label_en", columnDefinition = "TEXT")
    private String labelEn;

    @Column(name = "label_fr", columnDefinition = "TEXT")
    private String labelFr;

    @Column(name = "label_ki", columnDefinition = "TEXT")
    private String labelKi;

    @Column(name = "applies_to_all", nullable = false)
    private boolean appliesToAll;

    /**
     * If {@link #appliesToAll} is false, this collection defines which branches are affected.
     * If {@link #appliesToAll} is true, this should stay empty.
     */
    @ManyToMany
    @JoinTable(
            name = "calendar_entry_branches",
            joinColumns = @JoinColumn(name = "calendar_entry_id"),
            inverseJoinColumns = @JoinColumn(name = "branch_id")
    )
    private Set<Branch> branches = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

