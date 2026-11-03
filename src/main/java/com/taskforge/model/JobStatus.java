package com.taskforge.model;

/**
 * Represents the lifecycle stages of a Job in TaskForge.
 *
 * State machine transitions:
 * SUBMITTED -> QUEUED -> RUNNING -> COMPLETED
 *                                \-> FAILED
 * SUBMITTED -> FAILED (if rejected at submission)
 */
public enum JobStatus {
    /**
     * Job has been created and received by the engine.
     */
    SUBMITTED,

    /**
     * Job is waiting inside the bounded FIFO queue.
     */
    QUEUED,

    /**
     * Job has been dequeued by an active worker thread and is currently executing.
     */
    RUNNING,

    /**
     * Job execution completed successfully. Terminal state.
     */
    COMPLETED,

    /**
     * Job execution failed or was rejected due to queue capacity limits. Terminal state.
     */
    FAILED;

    /**
     * Returns true if this state is terminal (no further transitions allowed).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
