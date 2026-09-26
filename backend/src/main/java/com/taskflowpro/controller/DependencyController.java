package com.taskflowpro.controller;

import com.taskflowpro.dto.CreateDependencyRequest;
import com.taskflowpro.model.Dependency;
import com.taskflowpro.model.DependencySource;
import com.taskflowpro.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks/{taskId}/dependencies")
@RequiredArgsConstructor
public class DependencyController {

    private final TaskService taskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Dependency add(@PathVariable String taskId, @Valid @RequestBody CreateDependencyRequest req) {
        return taskService.addDependency(req.getPrerequisiteId(), taskId, DependencySource.MANUAL);
    }

    @DeleteMapping("/{prerequisiteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String taskId, @PathVariable String prerequisiteId) {
        taskService.removeDependency(prerequisiteId, taskId);
    }
}
