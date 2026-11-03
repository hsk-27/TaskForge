package com.taskforge.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskforge.domain.Job;
import com.taskforge.domain.JobType;
import org.springframework.stereotype.Component;

/**
 * CS Theory: CPU-Bound Workloads & Cooperative Thread Interruption
 *
 * Simulates a CPU-heavy computation (prime number search).
 *
 * Viva Note on Cooperative Thread Interruption:
 * Java deprecated Thread.stop() because it unsafely forces a thread to stop, releasing monitor locks
 * and leaving data in inconsistent states.
 * Instead, modern Java uses Cooperative Cancellation: when an operator cancels a job,
 * we call thread.interrupt(). Inside this loop, we explicitly check Thread.currentThread().isInterrupted()
 * so the worker can terminate gracefully without corrupting state.
 */
@Component
public class ComputeJobHandler implements JobHandler {

    private final ObjectMapper objectMapper;

    public ComputeJobHandler() {
        this(new ObjectMapper());
    }

    public ComputeJobHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public JobType supportsType() {
        return JobType.COMPUTE;
    }

    @Override
    public JobExecutionResult execute(Job job, String workerId) throws Exception {
        return execute(job, workerId, null);
    }

    @Override
    public JobExecutionResult execute(Job job, String workerId, JobProgressListener progressListener) throws Exception {
        int limit = 15000;
        String payload = job.getPayload();
        if (payload != null && !payload.isBlank()) {
            try {
                if (payload.trim().startsWith("{")) {
                    JsonNode node = objectMapper.readTree(payload);
                    if (node.has("limit")) {
                        limit = Math.min(50000, Math.max(1000, node.get("limit").asInt(15000)));
                    }
                }
            } catch (Exception ignored) {}
        }

        if (progressListener != null) {
            progressListener.onProgress(5, "Initializing calculation range [2, " + limit + "]");
        }

        int primeCount = 0;
        int progressStep = Math.max(1, limit / 10);
        for (int i = 2; i <= limit; i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Worker thread interrupted during COMPUTE workload");
            }
            if (i % 400 == 0) {
                // Cooperative micro-sleep to prevent 100% single-core saturation on local host
                Thread.sleep(1);
            }
            if (isPrime(i)) {
                primeCount++;
            }
            if (progressListener != null && (i % progressStep == 0 || i == limit)) {
                int pct = Math.min(99, (int) (((long) i * 100) / limit));
                progressListener.onProgress(pct, "Sieving primes (" + i + "/" + limit + ")");
            }
        }

        if (progressListener != null) {
            progressListener.onProgress(100, "Prime calculation finalized");
        }

        String output = String.format("Found %d primes in range [2, %d]", primeCount, limit);
        return JobExecutionResult.success("Compute task completed successfully", output);
    }

    private boolean isPrime(int n) {
        if (n <= 1) return false;
        if (n <= 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (int i = 5; i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }
}
