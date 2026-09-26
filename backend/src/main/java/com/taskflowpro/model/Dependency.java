package com.taskflowpro.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "dependencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dependency {

    @EmbeddedId
    private DependencyId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DependencySource source;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class DependencyId implements Serializable {
        @Column(name = "prerequisite_id")
        private String prerequisiteId;

        @Column(name = "task_id")
        private String taskId;
    }
}
