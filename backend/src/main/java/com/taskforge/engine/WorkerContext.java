package com.taskforge.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class WorkerContext {
    private final String workerId;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile String currentJobId;
    private volatile String currentJobName;
    private volatile Integer attemptNumber;
    private volatile Instant startedAt;

    private volatile int progressPercent = 0;
    private volatile String currentStage = "";

    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final AtomicLong totalExecutionTimeMs = new AtomicLong(0);

    public WorkerContext(String workerId) {
        this.workerId = workerId;
    }

    public void setBusy(String jobId, String jobName, int attemptNumber) {
        this.currentJobId = jobId;
        this.currentJobName = jobName;
        this.attemptNumber = attemptNumber;
        this.startedAt = Instant.now();
        this.progressPercent = 0;
        this.currentStage = "Claimed / Initializing";
        this.busy.set(true);
    }

    public void updateProgress(int percent, String stage) {
        this.progressPercent = Math.max(0, Math.min(100, percent));
        if (stage != null && !stage.isBlank()) {
            this.currentStage = stage;
        }
    }

    public void recordJobFinished(boolean success, long durationMs) {
        totalProcessed.incrementAndGet();
        if (!success) {
            totalFailed.incrementAndGet();
        }
        totalExecutionTimeMs.addAndGet(durationMs);
    }

    public void setIdle() {
        this.busy.set(false);
        this.currentJobId = null;
        this.currentJobName = null;
        this.attemptNumber = null;
        this.startedAt = null;
        this.progressPercent = 0;
        this.currentStage = "";
    }

    public String getWorkerId() {
        return workerId;
    }

    public boolean isBusy() {
        return busy.get();
    }

    public String getCurrentJobId() {
        return currentJobId;
    }

    public String getCurrentJobName() {
        return currentJobName;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public long getTotalProcessed() {
        return totalProcessed.get();
    }

    public long getTotalFailed() {
        return totalFailed.get();
    }

    public long getTotalExecutionTimeMs() {
        return totalExecutionTimeMs.get();
    }

    public Long getElapsedMs() {
        if (!busy.get() || startedAt == null) return null;
        return Duration.between(startedAt, Instant.now()).toMillis();
    }
}
