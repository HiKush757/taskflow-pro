package com.taskflowpro.service;

import com.taskflowpro.dto.TaskResponse;
import com.taskflowpro.engine.BlockedTaskException;
import com.taskflowpro.engine.CycleDetectedException;
import com.taskflowpro.engine.DagEngine;
import com.taskflowpro.model.*;
import com.taskflowpro.repository.DependencyRepository;
import com.taskflowpro.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates task and dependency changes: every mutation that can affect
 * the schedule or Blocked/Ready state runs inside one transaction, loads the
 * whole graph, delegates the actual logic to the (database-free) DagEngine,
 * then persists the result.
 *
 * <p>Loading the whole graph on every write is the simple, obviously-correct
 * choice for a board with dozens to a few hundred tasks. At much larger
 * scale this would instead load only the affected task's connected
 * component; see the README limitations section.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final DependencyRepository dependencyRepository;
    private final DagEngine dagEngine = new DagEngine();

    public List<TaskResponse> listTasks() {
        List<Task> all = taskRepository.findAll();
        Map<String, List<String>> prereqsByTask = allPrerequisitesGrouped();
        return all.stream()
                .map(t -> TaskResponse.of(t, prereqsByTask.getOrDefault(t.getId(), List.of())))
                .collect(Collectors.toList());
    }

    private TaskResponse toResponse(Task task) {
        List<String> prereqIds = dependencyRepository.findByIdTaskId(task.getId()).stream()
                .map(d -> d.getId().getPrerequisiteId())
                .collect(Collectors.toList());
        return TaskResponse.of(task, prereqIds);
    }

    private Map<String, List<String>> allPrerequisitesGrouped() {
        Map<String, List<String>> map = new HashMap<>();
        for (Dependency d : dependencyRepository.findAll()) {
            map.computeIfAbsent(d.getId().getTaskId(), k -> new ArrayList<>()).add(d.getId().getPrerequisiteId());
        }
        return map;
    }

    @Transactional
    public TaskResponse createTask(String title, String description, int durationDays, LocalDate plannedStart) {
        Task task = Task.builder()
                .title(title)
                .description(description)
                .status(TaskStatus.BACKLOG)
                .position(nextPositionFor(TaskStatus.BACKLOG))
                .durationDays(durationDays)
                .plannedStart(plannedStart)
                .blocked(false)
                .build();
        task = taskRepository.save(task);
        recomputeFrom(task.getId());
        return toResponse(taskRepository.findById(task.getId()).orElseThrow());
    }

    @Transactional
    public TaskResponse moveTask(String taskId, TaskStatus newStatus, int newPosition) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));

        boolean movingForward = newStatus != TaskStatus.BACKLOG;
        if (task.isBlocked() && movingForward) {
            throw new BlockedTaskException(taskId);
        }

        task.setStatus(newStatus);
        task.setPosition(newPosition);
        taskRepository.save(task);

        // A status change (including a DONE -> IN_PROGRESS rollback) can
        // affect every downstream task's Blocked/Ready state.
        recomputeFrom(taskId);
        return toResponse(taskRepository.findById(taskId).orElseThrow());
    }

    @Transactional
    public TaskResponse updateSchedule(String taskId, LocalDate plannedStart, Integer durationDays) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));
        if (plannedStart != null) task.setPlannedStart(plannedStart);
        if (durationDays != null) task.setDurationDays(durationDays);
        taskRepository.save(task);
        recomputeFrom(taskId);
        return toResponse(taskRepository.findById(taskId).orElseThrow());
    }

    @Transactional
    public Dependency addDependency(String prerequisiteId, String taskId, DependencySource source) {
        if (!taskRepository.existsById(prerequisiteId) || !taskRepository.existsById(taskId)) {
            throw new NoSuchElementException("Both tasks must exist.");
        }
        Map<String, Set<String>> prerequisitesByTask = loadPrerequisitesByTask();

        if (dagEngine.wouldCreateCycle(prerequisiteId, taskId, prerequisitesByTask)) {
            throw new CycleDetectedException(prerequisiteId, taskId);
        }

        Dependency dependency = Dependency.builder()
                .id(new Dependency.DependencyId(prerequisiteId, taskId))
                .source(source)
                .build();
        dependency = dependencyRepository.save(dependency);

        recomputeFrom(taskId);
        return dependency;
    }

    @Transactional
    public void removeDependency(String prerequisiteId, String taskId) {
        dependencyRepository.deleteByIdPrerequisiteIdAndIdTaskId(prerequisiteId, taskId);
        recomputeFrom(taskId);
    }

    /** Longest-duration chain in the whole graph, for the optional Critical Path view. */
    public Map<String, Integer> criticalPathLengths() {
        List<Task> all = taskRepository.findAll();
        Map<String, Task> tasksById = all.stream().collect(Collectors.toMap(Task::getId, t -> t));
        Map<String, Set<String>> prerequisitesByTask = loadPrerequisitesByTask();
        return dagEngine.longestPathDurations(tasksById.keySet(), tasksById, prerequisitesByTask);
    }

    // --- internal helpers -------------------------------------------------

    private void recomputeFrom(String taskId) {
        List<Task> all = taskRepository.findAll();
        Map<String, Task> tasksById = all.stream().collect(Collectors.toMap(Task::getId, t -> t));

        List<Dependency> deps = dependencyRepository.findAll();
        Map<String, Set<String>> prerequisitesByTask = new HashMap<>();
        Map<String, Set<String>> dependentsByTask = new HashMap<>();
        for (Dependency d : deps) {
            String prereq = d.getId().getPrerequisiteId();
            String task = d.getId().getTaskId();
            prerequisitesByTask.computeIfAbsent(task, k -> new HashSet<>()).add(prereq);
            dependentsByTask.computeIfAbsent(prereq, k -> new HashSet<>()).add(task);
        }

        dagEngine.recompute(taskId, tasksById, prerequisitesByTask, dependentsByTask);
        taskRepository.saveAll(tasksById.values());
    }

    private Map<String, Set<String>> loadPrerequisitesByTask() {
        Map<String, Set<String>> map = new HashMap<>();
        for (Dependency d : dependencyRepository.findAll()) {
            map.computeIfAbsent(d.getId().getTaskId(), k -> new HashSet<>()).add(d.getId().getPrerequisiteId());
        }
        return map;
    }

    private int nextPositionFor(TaskStatus status) {
        return (int) taskRepository.findAll().stream().filter(t -> t.getStatus() == status).count();
    }
}
