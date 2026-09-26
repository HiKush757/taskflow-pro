package com.taskflowpro.engine;

/** Thrown when adding a dependency edge would close a cycle in the graph. */
public class CycleDetectedException extends RuntimeException {

    public CycleDetectedException(String prerequisiteId, String taskId) {
        super("Adding prerequisite " + prerequisiteId + " -> " + taskId
                + " would create a circular dependency. Rejected; no change was persisted.");
    }
}
