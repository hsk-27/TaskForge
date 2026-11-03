package com.taskforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Historical record of a single execution attempt by a worker thread.
 */
@Entity
@Table(name = "job_attempts")
public class JobAttempt {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "job_id", length = 36, nullable = false)
    private String jobId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "worker_id", length = 100, nullable = false)
    private String workerId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "outcome", length = 50, nullable = false)
    private String outcome;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    protected JobAttempt() {
    }

    public JobAttempt(String jobId, int attemptNumber, String workerId, Instant startedAt) {
        this.id = UUID.randomUUID().toString();
        this.jobId = Objects.requireNonNull(jobId, "Job ID cannot be null");
        this.attemptNumber = attemptNumber;
        this.workerId = Objects.requireNonNull(workerId, "Worker ID cannot be null");
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.outcome = "RUNNING";
    }

    public void complete(String outcome, String errorMessage, Instant completedAt) {
        this.outcome = outcome;
        this.errorMessage = errorMessage;
        this.completedAt = completedAt != null ? completedAt : Instant.now();
        this.durationMs = (this.startedAt != null && this.completedAt != null)
                ? Duration.between(this.startedAt, this.completedAt).toMillis()
                : null;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
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

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
}
