package com.taskflowpro.dto;

import com.taskflowpro.model.Task;
import com.taskflowpro.model.TaskStatus;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

/**
 * Task plus its prerequisite ids, so the frontend can render the
 * dependency list and the add/remove-prerequisite UI without a second
 * round trip per card.
 */
@Value
public class TaskResponse {
    String id;
    String title;
    String description;
    TaskStatus status;
    Integer position;
    Integer durationDays;
    LocalDate plannedStart;
    LocalDate scheduledStart;
    LocalDate scheduledEnd;
    boolean blocked;
    List<String> prerequisiteIds;

    public static TaskResponse of(Task task, List<String> prerequisiteIds) {
        return new TaskResponse(
                task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                task.getPosition(), task.getDurationDays(), task.getPlannedStart(),
                task.getScheduledStart(), task.getScheduledEnd(), task.isBlocked(),
                prerequisiteIds);
    }
}
