package com.taskflowpro.engine;

import com.taskflowpro.model.Task;
import com.taskflowpro.model.TaskStatus;

import java.time.LocalDate;
import java.util.*;

/**
 * Pure, database-free DAG logic: cycle detection, topological ordering, and
 * schedule / blocked-state propagation. Everything here operates on plain
 * maps handed in by the caller, so it can be unit tested without Spring or a
 * database.
 *
 * <p>Edge convention: a dependency edge runs prerequisiteId -&gt; taskId,
 * meaning taskId cannot start until prerequisiteId is Done. Callers represent
 * the graph as {@code prerequisitesByTask}: taskId -&gt; the set of ids it
 * depends on.
 */
public class DagEngine {

    /**
     * True if adding an edge (prerequisiteId -&gt; taskId) would create a
     * cycle, given the dependencies that already exist. A self-dependency
     * always counts as a cycle.
     *
     * <p>Reasoning: the new edge closes a loop exactly when taskId is
     * already a (transitive) prerequisite of prerequisiteId — i.e. a path
     * taskId -&gt; ... -&gt; prerequisiteId already exists. We check this by
     * walking backward from prerequisiteId through its own prerequisites.
     */
    public boolean wouldCreateCycle(String prerequisiteId, String taskId,
                                     Map<String, Set<String>> prerequisitesByTask) {
        if (Objects.equals(prerequisiteId, taskId)) {
            return true;
        }
        Deque<String> stack = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        stack.push(prerequisiteId);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(taskId)) {
                return true;
            }
            for (String p : prerequisitesByTask.getOrDefault(current, Collections.emptySet())) {
                stack.push(p);
            }
        }
        return false;
    }

    /**
     * Topological order over every id in {@code allIds}, using Kahn's
     * algorithm. Throws IllegalStateException if a cycle is present — this
     * should never happen in practice since {@link #wouldCreateCycle} is
     * checked before every insert, but the engine defends against it anyway
     * rather than silently mis-scheduling.
     */
    public List<String> topologicalOrder(Collection<String> allIds,
                                          Map<String, Set<String>> prerequisitesByTask) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> forward = new HashMap<>();
        for (String id : allIds) {
            inDegree.put(id, 0);
            forward.put(id, new ArrayList<>());
        }
        for (String id : allIds) {
            for (String prereq : prerequisitesByTask.getOrDefault(id, Collections.emptySet())) {
                if (!forward.containsKey(prereq)) {
                    continue; // prerequisite outside the requested id set
                }
                forward.get(prereq).add(id);
                inDegree.merge(id, 1, Integer::sum);
            }
        }
        Deque<String> queue = new ArrayDeque<>();
        for (String id : allIds) {
            if (inDegree.get(id) == 0) {
                queue.add(id);
            }
        }
        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            order.add(id);
            for (String dependent : forward.get(id)) {
                int remaining = inDegree.merge(dependent, -1, Integer::sum);
                if (remaining == 0) {
                    queue.add(dependent);
                }
            }
        }
        if (order.size() != allIds.size()) {
            throw new IllegalStateException("Cycle detected during topological sort — graph invariant violated.");
        }
        return order;
    }

    /** Forward BFS: every id reachable from {@code startId} via dependents (not including startId itself). */
    public Set<String> descendantsOf(String startId, Map<String, Set<String>> dependentsByTask) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        visited.add(startId);
        Set<String> descendants = new HashSet<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : dependentsByTask.getOrDefault(current, Collections.emptySet())) {
                if (visited.add(next)) {
                    descendants.add(next);
                    queue.add(next);
                }
            }
        }
        return descendants;
    }

    /**
     * Recomputes scheduledStart / scheduledEnd / blocked for {@code changedTaskId}
     * and every task downstream of it, in topological order, then leaves the
     * results set directly on the {@link Task} objects in {@code tasksById}
     * (the caller persists them).
     *
     * <p>Because each task's start is {@code max(plannedStart, max(prerequisite
     * scheduledEnd))} rather than a running sum, converging paths in a diamond
     * never compound: a task with two delayed prerequisites shifts by the
     * largest single delay, not the total of both paths. Re-running this
     * method on an already-consistent graph is a no-op (idempotent).
     */
    public void recompute(String changedTaskId,
                           Map<String, Task> tasksById,
                           Map<String, Set<String>> prerequisitesByTask,
                           Map<String, Set<String>> dependentsByTask) {
        Set<String> affected = new HashSet<>(descendantsOf(changedTaskId, dependentsByTask));
        affected.add(changedTaskId);

        List<String> order = topologicalOrder(tasksById.keySet(), prerequisitesByTask);

        for (String id : order) {
            if (!affected.contains(id)) {
                continue; // untouched task: its existing values are still valid inputs downstream
            }
            Task task = tasksById.get(id);
            Set<String> prereqIds = prerequisitesByTask.getOrDefault(id, Collections.emptySet());

            LocalDate earliestStart = task.getPlannedStart();
            boolean blocked = false;

            for (String prereqId : prereqIds) {
                Task prereq = tasksById.get(prereqId);
                if (prereq == null) {
                    continue;
                }
                if (prereq.getStatus() != TaskStatus.DONE) {
                    blocked = true;
                }
                LocalDate prereqEnd = prereq.getScheduledEnd();
                if (prereqEnd != null && (earliestStart == null || prereqEnd.isAfter(earliestStart))) {
                    earliestStart = prereqEnd;
                }
            }

            task.setScheduledStart(earliestStart);
            task.setScheduledEnd(earliestStart == null ? null : earliestStart.plusDays(task.getDurationDays()));
            task.setBlocked(blocked);
        }
    }

    /** Longest path (by duration) ending at each task; the overall critical path is the max total. */
    public Map<String, Integer> longestPathDurations(Collection<String> allIds,
                                                      Map<String, Task> tasksById,
                                                      Map<String, Set<String>> prerequisitesByTask) {
        List<String> order = topologicalOrder(allIds, prerequisitesByTask);
        Map<String, Integer> longest = new HashMap<>();
        for (String id : order) {
            int duration = tasksById.get(id).getDurationDays();
            int best = 0;
            for (String prereqId : prerequisitesByTask.getOrDefault(id, Collections.emptySet())) {
                best = Math.max(best, longest.getOrDefault(prereqId, 0));
            }
            longest.put(id, best + duration);
        }
        return longest;
    }
}
