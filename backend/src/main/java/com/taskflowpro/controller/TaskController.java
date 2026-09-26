package com.taskflowpro.controller;

import com.taskflowpro.dto.CreateTaskRequest;
import com.taskflowpro.dto.MoveTaskRequest;
import com.taskflowpro.dto.TaskResponse;
import com.taskflowpro.dto.UpdateScheduleRequest;
import com.taskflowpro.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public List<TaskResponse> list() {
        return taskService.listTasks();
    }

    @PostMapping
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest req) {
        return taskService.createTask(req.getTitle(), req.getDescription(), req.getDurationDays(), req.getPlannedStart());
    }

    @PatchMapping("/{id}/move")
    public TaskResponse move(@PathVariable String id, @Valid @RequestBody MoveTaskRequest req) {
        return taskService.moveTask(id, req.getStatus(), req.getPosition());
    }

    @PatchMapping("/{id}/schedule")
    public TaskResponse updateSchedule(@PathVariable String id, @RequestBody UpdateScheduleRequest req) {
        return taskService.updateSchedule(id, req.getPlannedStart(), req.getDurationDays());
    }

    @GetMapping("/critical-path")
    public Map<String, Integer> criticalPath() {
        return taskService.criticalPathLengths();
    }
}
