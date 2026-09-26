package com.taskflowpro.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ai_suggestions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSuggestion {

    @Id
    private String id;

    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Column(name = "candidate_id", nullable = false)
    private String candidateId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuggestionState state;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.id == null) {
            this.id = java.util.UUID.randomUUID().toString();
        }
    }
}
