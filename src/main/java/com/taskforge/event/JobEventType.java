package com.taskforge.event;

/**
 * Enumeration of discrete lifecycle events during job execution.
 */
public enum JobEventType {
    SUBMITTED,
    ENQUEUED,
    REJECTED,
    DEQUEUED,
    STARTED,
    COMPLETED,
    FAILED
}
