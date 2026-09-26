package com.taskflowpro.dto;

import com.taskflowpro.model.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MoveTaskRequest {
    @NotNull
    private TaskStatus status;
    @NotNull
    private Integer position;
}
