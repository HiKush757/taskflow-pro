package com.taskflowpro.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    /** Position within its column, used for ordering cards on the board. */
    @Column(name = "board_position", nullable = false)
    private Integer position;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    /** The date the task would start if it had no prerequisites. */
    @Column(name = "planned_start")
    private LocalDate plannedStart;

    /** Derived: max(plannedStart, latest scheduledEnd among prerequisites). */
    @Column(name = "scheduled_start")
    private LocalDate scheduledStart;

    /** Derived: scheduledStart + durationDays. */
    @Column(name = "scheduled_end")
    private LocalDate scheduledEnd;

    /** Derived: true if any prerequisite is not yet DONE. */
    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    @Version
    private Long version;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.id == null) {
            this.id = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
