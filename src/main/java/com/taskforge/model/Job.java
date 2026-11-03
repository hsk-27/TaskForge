package com.taskforge.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Core entity representing a unit of work submitted to TaskForge.
 *
 * Thread Safety:
 * - Immutable identifiers (id, name, createdAt, action) are safely readable without synchronization.
 * - Mutable lifecycle states and timestamps are protected by synchronized methods to ensure atomic state transitions
 *   and enforce the valid lifecycle state machine.
 */
public class Job {
    private final String id;
    private final String name;
    private final JobAction action;
    private final Instant createdAt;

    // Guarded by this intrinsic lock
    private JobStatus status;
    private int attemptCount;
    private Instant enqueuedAt;
    private Instant startedAt;
    private Instant completedAt;
    private String assignedWorker;
    private JobResult result;

    public Job(String id, String name, JobAction action) {
        this.id = Objects.requireNonNull(id, "Job ID cannot be null");
        this.name = Objects.requireNonNull(name, "Job name cannot be null");
        this.action = Objects.requireNonNull(action, "Job action cannot be null");
        this.createdAt = Instant.now();
        this.status = JobStatus.SUBMITTED;
        this.attemptCount = 0;
    }

    public static Job of(String name, JobAction action) {
        return new Job("job-" + UUID.randomUUID().toString().substring(0, 8), name, action);
    }

    public static Job of(String id, String name, JobAction action) {
        return new Job(id, name, action);
    }

    // --- State Machine Transitions ---

    /**
     * Transitions status from SUBMITTED to QUEUED when placed in the buffer.
     */
    public synchronized void markQueued(Instant timestamp) {
        if (this.status != JobStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Cannot transition job " + id + " to QUEUED from " + this.status);
        }
        this.status = JobStatus.QUEUED;
        this.enqueuedAt = timestamp != null ? timestamp : Instant.now();
    }

    /**
     * Transitions status from QUEUED to RUNNING when claimed by a worker thread.
     */
    public synchronized void markStarted(String workerThreadName, Instant timestamp) {
        if (this.status != JobStatus.QUEUED) {
            throw new IllegalStateException(
                    "Cannot transition job " + id + " to RUNNING from " + this.status);
        }
        this.status = JobStatus.RUNNING;
        this.assignedWorker = workerThreadName;
        this.attemptCount++;
        this.startedAt = timestamp != null ? timestamp : Instant.now();
    }

    /**
     * Transitions status from RUNNING to COMPLETED.
     */
    public synchronized void markCompleted(JobResult jobResult, Instant timestamp) {
        if (this.status != JobStatus.RUNNING) {
            throw new IllegalStateException(
                    "Cannot transition job " + id + " to COMPLETED from " + this.status);
        }
        this.status = JobStatus.COMPLETED;
        this.result = Objects.requireNonNull(jobResult, "JobResult cannot be null");
        this.completedAt = timestamp != null ? timestamp : Instant.now();
    }

    /**
     * Transitions status to FAILED.
     * Can happen from RUNNING (runtime error) or SUBMITTED (queue rejection).
     */
    public synchronized void markFailed(JobResult jobResult, Instant timestamp) {
        if (this.status.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot transition job " + id + " to FAILED because it is already terminal (" + this.status + ")");
        }
        this.status = JobStatus.FAILED;
        this.result = Objects.requireNonNull(jobResult, "JobResult cannot be null");
        this.completedAt = timestamp != null ? timestamp : Instant.now();
    }

    // --- Getters ---

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public JobAction getAction() {
        return action;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public synchronized JobStatus getStatus() {
        return status;
    }

    public synchronized int getAttemptCount() {
        return attemptCount;
    }

    public synchronized Instant getEnqueuedAt() {
        return enqueuedAt;
    }

    public synchronized Instant getStartedAt() {
        return startedAt;
    }

    public synchronized Instant getCompletedAt() {
        return completedAt;
    }

    public synchronized String getAssignedWorker() {
        return assignedWorker;
    }

    public synchronized JobResult getResult() {
        return result;
    }

    /**
     * Time spent waiting in the queue before execution started.
     */
    public synchronized Duration getQueueWaitDuration() {
        if (enqueuedAt == null || startedAt == null) {
            return Duration.ZERO;
        }
        return Duration.between(enqueuedAt, startedAt);
    }

    /**
     * Time spent actively running on a worker thread.
     */
    public synchronized Duration getExecutionDuration() {
        if (startedAt == null) {
            return Duration.ZERO;
        }
        Instant end = completedAt != null ? completedAt : Instant.now();
        return Duration.between(startedAt, end);
    }

    /**
     * Total elapsed time from creation to completion.
     */
    public synchronized Duration getTotalDuration() {
        Instant end = completedAt != null ? completedAt : Instant.now();
        return Duration.between(createdAt, end);
    }

    @Override
    public synchronized String toString() {
        return "Job{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", attempts=" + attemptCount +
                ", worker='" + assignedWorker + '\'' +
                ", result=" + result +
                '}';
    }
}
