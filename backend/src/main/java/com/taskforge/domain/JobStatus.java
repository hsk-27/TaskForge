package com.taskforge.domain;

import java.util.Set;

/**
 * Explicit state machine for TaskForge Job lifecycle.
 *
 * State Machine Graph:
 * QUEUED     ──> RUNNING ──> SUCCEEDED (terminal)
 *                 │    │
 *                 │    └───> RETRY_WAIT ──> QUEUED
 *                 │
 *                 └───> FAILED (terminal)
 *
 * QUEUED     ──> CANCELLED (terminal)
 * QUEUED     ──> FAILED (terminal, prerequisite failure / unresolvable dependency)
 * RETRY_WAIT ──> CANCELLED (terminal)
 * RETRY_WAIT ──> FAILED (terminal)
 * RUNNING    ──> CANCELLED (terminal, cooperative)
 */
public enum JobStatus {
    QUEUED,
    RUNNING,
    RETRY_WAIT,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    public boolean canTransitionTo(JobStatus target) {
        if (this == target) return true;
        if (this.isTerminal()) return false; // Terminal states cannot transition

        return switch (this) {
            case QUEUED -> target == RUNNING || target == CANCELLED || target == FAILED;
            case RUNNING -> target == SUCCEEDED || target == FAILED || target == RETRY_WAIT || target == CANCELLED;
            case RETRY_WAIT -> target == QUEUED || target == CANCELLED || target == FAILED;
            default -> false;
        };
    }
}
