package com.taskflowpro.engine;

/** Thrown when trying to advance a Blocked task past Backlog. */
public class BlockedTaskException extends RuntimeException {

    public BlockedTaskException(String taskId) {
        super("Task " + taskId + " is Blocked and cannot move forward until its prerequisites are Done.");
    }
}
