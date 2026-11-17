package com.taskforge.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskforge.domain.Job;
import com.taskforge.domain.JobType;
import org.springframework.stereotype.Component;

/**
 * Chaos Engineering & Deterministic Fault Injection Handler
 *
 * Implements deterministic fault scenarios (network flaps, uncaught exceptions, rate limit rolls).
 * This enables the 'Failure Lab' where operators can test and validate resilience guarantees:
 * 1. Transient Flap: Job fails on attempts 1 and 2, but succeeds on attempt 3.
 * 2. Unrecoverable Crash: Job fails all attempts, moving permanently into the Dead Letter Queue (DLQ).
 * 3. Exponential Backoff Verification: Confirms retry delays double with each successive attempt.
 */
@Component
public class FailureDemoJobHandler implements JobHandler {

    private final ObjectMapper objectMapper;

    public FailureDemoJobHandler() {
        this(new ObjectMapper());
    }

    public FailureDemoJobHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public JobType supportsType() {
        return JobType.FAILURE_DEMO;
    }

    @Override
    public JobExecutionResult execute(Job job, String workerId) throws Exception {
        return execute(job, workerId, null);
    }

    @Override
    public JobExecutionResult execute(Job job, String workerId, JobProgressListener progressListener) throws Exception {
        if (progressListener != null) {
            progressListener.onProgress(25, "Evaluating failure injection rules");
        }

        // Small execution delay
        Thread.sleep(60);

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Worker thread interrupted during FAILURE_DEMO");
        }

        String payload = job.getPayload() != null ? job.getPayload() : "";

        // Scenario 1: Throw uncaught runtime exception
        if (payload.contains("THROW_UNCAUGHT")) {
            throw new RuntimeException("Simulated catastrophic uncaught exception in worker: " + workerId);
        }

        JsonNode jsonNode = null;
        if (payload.trim().startsWith("{")) {
            try {
                jsonNode = objectMapper.readTree(payload);
            } catch (Exception ignored) {}
        }

        if (progressListener != null) {
            progressListener.onProgress(60, "Executing deterministic error simulation");
        }

        // Scenario 2: Fail until a specific attempt number is reached (e.g. fail attempts 1 & 2, succeed on 3)
        if (jsonNode != null && (jsonNode.has("failUntilAttempt") || jsonNode.has("failAttempts"))) {
            int targetAttempt = jsonNode.has("failUntilAttempt")
                    ? jsonNode.get("failUntilAttempt").asInt(2)
                    : jsonNode.get("failAttempts").asInt(2) + 1;

            if (job.getAttemptCount() < targetAttempt) {
                return JobExecutionResult.failure(
                        String.format("Deterministic failure on attempt %d (will succeed on attempt %d)",
                                job.getAttemptCount(), targetAttempt));
            } else {
                if (progressListener != null) {
                    progressListener.onProgress(100, "Self-healing retry succeeded");
                }
                return JobExecutionResult.success(
                        String.format("Recovered on attempt %d after transient failures", job.getAttemptCount()),
                        "Self-healing retry succeeded");
            }
        }

        // Scenario 3: Deterministic pseudo-random failure based on job ID hash and failure rate
        if (jsonNode != null && jsonNode.has("failureRate")) {
            double rate = jsonNode.get("failureRate").asDouble(0.5);
            int hash = Math.abs((job.getId() + "-" + job.getAttemptCount()).hashCode());
            double roll = (hash % 1000) / 1000.0;
            if (roll < rate) {
                return JobExecutionResult.failure(
                        String.format("Simulated rate failure (roll=%.2f < rate=%.2f): External API timeout", roll, rate));
            } else {
                if (progressListener != null) {
                    progressListener.onProgress(100, "Service responded within SLA");
                }
                return JobExecutionResult.success("Simulated rate check passed", "Service responded within SLA");
            }
        }

        // Default: deterministic simulated failure
        return JobExecutionResult.failure("Deterministic failure injected: Simulated DB connection lockup");
    }
}
