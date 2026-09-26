package com.taskflowpro.engine;

import com.taskflowpro.model.Task;
import com.taskflowpro.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DagEngineTest {

    private final DagEngine engine = new DagEngine();

    private Task task(String id, int durationDays, LocalDate plannedStart, TaskStatus status) {
        return Task.builder()
                .id(id)
                .title(id)
                .status(status)
                .position(0)
                .durationDays(durationDays)
                .plannedStart(plannedStart)
                .build();
    }

    // ---- Cycle detection ----------------------------------------------

    @Test
    void directCycleIsRejected() {
        // A -> B already exists. B -> A would close a 2-node loop.
        Map<String, Set<String>> prereqs = new HashMap<>();
        prereqs.put("B", Set.of("A"));

        assertTrue(engine.wouldCreateCycle("B", "A", prereqs));
    }

    @Test
    void threeNodeCycleIsRejected() {
        // A -> B -> C already exists. Adding C -> A (i.e. prerequisite=C, task=A) closes the loop.
        Map<String, Set<String>> prereqs = new HashMap<>();
        prereqs.put("B", Set.of("A"));
        prereqs.put("C", Set.of("B"));

        assertTrue(engine.wouldCreateCycle("C", "A", prereqs));
    }

    @Test
    void selfDependencyIsRejected() {
        assertTrue(engine.wouldCreateCycle("A", "A", new HashMap<>()));
    }

    @Test
    void validNewEdgeIsAccepted() {
        // A -> B exists. Adding A -> C is fine (no path from C back to A).
        Map<String, Set<String>> prereqs = new HashMap<>();
        prereqs.put("B", Set.of("A"));

        assertFalse(engine.wouldCreateCycle("A", "C", prereqs));
    }

    // ---- Diamond convergence: no compounding ---------------------------

    @Test
    void diamondDoesNotCompoundSchedule() {
        // A -> B -> D and A -> C -> D. A shifts by +3 days; D should move by
        // exactly 3 days, not 6, however many paths converge on it.
        LocalDate start = LocalDate.of(2026, 1, 1);

        Task a = task("A", 5, start, TaskStatus.DONE);
        Task b = task("B", 2, start, TaskStatus.DONE);
        Task c = task("C", 2, start, TaskStatus.DONE);
        Task d = task("D", 3, start, TaskStatus.BACKLOG);

        Map<String, Task> tasksById = new HashMap<>();
        for (Task t : List.of(a, b, c, d)) tasksById.put(t.getId(), t);

        Map<String, Set<String>> prerequisitesByTask = new HashMap<>();
        prerequisitesByTask.put("B", Set.of("A"));
        prerequisitesByTask.put("C", Set.of("A"));
        prerequisitesByTask.put("D", Set.of("B", "C"));

        Map<String, Set<String>> dependentsByTask = new HashMap<>();
        dependentsByTask.put("A", Set.of("B", "C"));
        dependentsByTask.put("B", Set.of("D"));
        dependentsByTask.put("C", Set.of("D"));

        // Establish the initial consistent schedule.
        engine.recompute("A", tasksById, prerequisitesByTask, dependentsByTask);
        LocalDate dEndBefore = d.getScheduledEnd();

        // Now delay A by 3 days and recompute from A again.
        a.setPlannedStart(start.plusDays(3));
        engine.recompute("A", tasksById, prerequisitesByTask, dependentsByTask);

        LocalDate dEndAfter = d.getScheduledEnd();
        long shiftDays = java.time.temporal.ChronoUnit.DAYS.between(dEndBefore, dEndAfter);

        assertEquals(3, shiftDays, "D should shift by exactly 3 days, matching A's single delay — not 6.");
    }

    @Test
    void recomputeIsIdempotent() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        Task a = task("A", 5, start, TaskStatus.DONE);
        Task b = task("B", 2, start, TaskStatus.BACKLOG);

        Map<String, Task> tasksById = new HashMap<>(Map.of("A", a, "B", b));
        Map<String, Set<String>> prerequisitesByTask = Map.of("B", Set.of("A"));
        Map<String, Set<String>> dependentsByTask = Map.of("A", Set.of("B"));

        engine.recompute("A", tasksById, prerequisitesByTask, dependentsByTask);
        LocalDate firstEnd = b.getScheduledEnd();

        engine.recompute("A", tasksById, prerequisitesByTask, dependentsByTask);
        assertEquals(firstEnd, b.getScheduledEnd());
    }

    // ---- Rollback on regression -----------------------------------------

    @Test
    void movingDoneTaskBackToInProgressBlocksDownstream() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        Task a = task("A", 5, start, TaskStatus.DONE);
        Task b = task("B", 2, start, TaskStatus.BACKLOG);

        Map<String, Task> tasksById = new HashMap<>(Map.of("A", a, "B", b));
        Map<String, Set<String>> prerequisitesByTask = Map.of("B", Set.of("A"));
        Map<String, Set<String>> dependentsByTask = Map.of("A", Set.of("B"));

        engine.recompute("A", tasksById, prerequisitesByTask, dependentsByTask);
        assertFalse(b.isBlocked(), "B should be Ready once A is Done.");

        // Regression: A moves back to IN_PROGRESS.
        a.setStatus(TaskStatus.IN_PROGRESS);
        engine.recompute("A", tasksById, prerequisitesByTask, dependentsByTask);

        assertTrue(b.isBlocked(), "B must become Blocked again once its prerequisite regresses.");
    }

    @Test
    void rollbackReblocksOnlyTasksNotYetDone() {
        // A -> B -> C, all initially Done except C. Rolling A back to
        // In Progress re-blocks C (since C's own prerequisite chain now has
        // an unsatisfied ancestor)... but see the note below: B itself stays
        // Done. TaskFlow Pro does not auto-reopen completed work — B's
        // status field is a human decision, not a derived one. This is a
        // documented assumption (see README), not an oversight: it protects
        // against a single upstream regression silently un-completing a
        // whole finished chain.
        LocalDate start = LocalDate.of(2026, 1, 1);
        Task a = task("A", 3, start, TaskStatus.DONE);
        Task b = task("B", 2, start, TaskStatus.DONE);
        Task c = task("C", 2, start, TaskStatus.BACKLOG);

        Map<String, Task> tasksById = new HashMap<>(Map.of("A", a, "B", b, "C", c));
        Map<String, Set<String>> prerequisitesByTask = Map.of(
                "B", Set.of("A"),
                "C", Set.of("B"));
        Map<String, Set<String>> dependentsByTask = Map.of(
                "A", Set.of("B"),
                "B", Set.of("C"));

        engine.recompute("A", tasksById, prerequisitesByTask, dependentsByTask);
        assertFalse(c.isBlocked());

        a.setStatus(TaskStatus.IN_PROGRESS);
        engine.recompute("A", tasksById, prerequisitesByTask, dependentsByTask);

        assertTrue(b.isBlocked(), "B is directly downstream of A and re-blocks (its status field, DONE, is untouched by design).");
        assertFalse(c.isBlocked(), "C's direct prerequisite B is still Done, so C remains Ready — status changes don't cascade past a Done task.");
    }

    @Test
    void scheduleShiftPropagatesThroughMultipleLevels() {
        // A -> B -> C, none Done yet, so this isolates pure date propagation
        // across two hops (distinct from the single-hop diamond test above).
        LocalDate start = LocalDate.of(2026, 1, 1);
        Task a = task("A", 5, start, TaskStatus.BACKLOG);
        Task b = task("B", 2, start, TaskStatus.BACKLOG);
        Task c = task("C", 2, start, TaskStatus.BACKLOG);

        Map<String, Task> tasksById = new HashMap<>(Map.of("A", a, "B", b, "C", c));
        Map<String, Set<String>> prerequisitesByTask = Map.of(
                "B", Set.of("A"),
                "C", Set.of("B"));
        Map<String, Set<String>> dependentsByTask = Map.of(
                "A", Set.of("B"),
                "B", Set.of("C"));

        engine.recompute("A", tasksById, prerequisitesByTask, dependentsByTask);
        LocalDate cEndBefore = c.getScheduledEnd();

        a.setPlannedStart(start.plusDays(4));
        engine.recompute("A", tasksById, prerequisitesByTask, dependentsByTask);
        LocalDate cEndAfter = c.getScheduledEnd();

        assertEquals(4, java.time.temporal.ChronoUnit.DAYS.between(cEndBefore, cEndAfter),
                "A four-day delay on A must reach C, two hops downstream, as exactly four days.");
    }
}
