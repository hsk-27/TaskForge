package com.taskforge.domain;

import com.taskforge.exception.InvalidStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * CS Theory: Domain-Driven Entity & Finite State Machine (FSM)
 *
 * Represents an individual background task in the system.
 * Key Engineering Concepts:
 * 1. Optimistic Locking: The @Version field guards against lost updates. If two worker threads
 *    attempt to transition the same job concurrently, Hibernate detects the version mismatch
 *    and prevents race conditions without expensive pessimistic row locks.
 * 2. Finite State Machine (FSM): Valid state transitions (QUEUED -> RUNNING -> SUCCEEDED/FAILED/RETRY_WAIT)
 *    are enforced at the entity level via transitionTo(), preventing invalid states.
 */
@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", length = 50, nullable = false)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private JobStatus status;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "scheduled_execution_at")
    private Instant scheduledExecutionAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "worker_id", length = 100)
    private String workerId;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    @Column(name = "queue_wait_duration_ms")
    private Long queueWaitDurationMs;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Job() {
    }

    public Job(String name, JobType jobType, int priority, String payload, int maxAttempts) {
        this.id = UUID.randomUUID().toString();
        this.name = Objects.requireNonNull(name, "Job name cannot be null");
        this.jobType = Objects.requireNonNull(jobType, "JobType cannot be null");
        this.status = JobStatus.QUEUED;
        this.priority = priority;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.attemptCount = 0;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.version = 0L;
    }

    public void transitionTo(JobStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new InvalidStateTransitionException(
                    "Invalid state transition for job " + id + " from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
    }

    public void markRunning(String workerId, Instant timestamp) {
        transitionTo(JobStatus.RUNNING);
        this.workerId = workerId;
        this.attemptCount++;
        this.startedAt = timestamp != null ? timestamp : Instant.now();
        if (this.queueWaitDurationMs == null && this.createdAt != null) {
            this.queueWaitDurationMs = Duration.between(this.createdAt, this.startedAt).toMillis();
        }
    }

    public void markSucceeded(Instant timestamp) {
        transitionTo(JobStatus.SUCCEEDED);
        this.completedAt = timestamp != null ? timestamp : Instant.now();
        if (this.startedAt != null) {
            this.executionDurationMs = Duration.between(this.startedAt, this.completedAt).toMillis();
        }
        this.lastErrorMessage = null;
    }

    public void markFailed(String errorMessage, Instant timestamp) {
        transitionTo(JobStatus.FAILED);
        this.lastErrorMessage = errorMessage;
        this.completedAt = timestamp != null ? timestamp : Instant.now();
        if (this.startedAt != null) {
            this.executionDurationMs = Duration.between(this.startedAt, this.completedAt).toMillis();
        }
    }

    public void markRetryWait(String errorMessage, Instant nextScheduledAt) {
        transitionTo(JobStatus.RETRY_WAIT);
        this.lastErrorMessage = errorMessage;
        this.scheduledExecutionAt = nextScheduledAt;
        if (this.startedAt != null) {
            this.executionDurationMs = Duration.between(this.startedAt, Instant.now()).toMillis();
        }
    }

    public void markRequeued() {
        transitionTo(JobStatus.QUEUED);
        this.scheduledExecutionAt = null;
        this.startedAt = null;
    }

    public void markCancelled() {
        transitionTo(JobStatus.CANCELLED);
        this.completedAt = Instant.now();
        if (this.startedAt != null && this.executionDurationMs == null) {
            this.executionDurationMs = Duration.between(this.startedAt, this.completedAt).toMillis();
        }
    }

    public void resetForManualRetry() {
        if (this.status != JobStatus.FAILED && this.status != JobStatus.CANCELLED) {
            throw new InvalidStateTransitionException(
                    "Only FAILED or CANCELLED jobs can be reset for retry. Current status: " + this.status);
        }
        this.status = JobStatus.QUEUED;
        this.scheduledExecutionAt = null;
        this.startedAt = null;
        this.completedAt = null;
        this.lastErrorMessage = null;
        this.executionDurationMs = null;
        if (this.attemptCount >= this.maxAttempts) {
            this.maxAttempts = this.attemptCount + 3;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JobType getJobType() {
        return jobType;
    }

    public void setJobType(JobType jobType) {
        this.jobType = jobType;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getScheduledExecutionAt() {
        return scheduledExecutionAt;
    }

    public void setScheduledExecutionAt(Instant scheduledExecutionAt) {
        this.scheduledExecutionAt = scheduledExecutionAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public Long getExecutionDurationMs() {
        return executionDurationMs;
    }

    public void setExecutionDurationMs(Long executionDurationMs) {
        this.executionDurationMs = executionDurationMs;
    }

    public Long getQueueWaitDurationMs() {
        return queueWaitDurationMs;
    }

    public void setQueueWaitDurationMs(Long queueWaitDurationMs) {
        this.queueWaitDurationMs = queueWaitDurationMs;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
