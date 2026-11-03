package com.taskforge.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskforge.domain.Job;
import com.taskforge.domain.JobType;
import org.springframework.stereotype.Component;

/**
 * Simulates external I/O latency (HTTP service, S3 blob download, database query).
 */
@Component
public class SimulatedIoJobHandler implements JobHandler {

    private final ObjectMapper objectMapper;

    public SimulatedIoJobHandler() {
        this(new ObjectMapper());
    }

    public SimulatedIoJobHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public JobType supportsType() {
        return JobType.SIMULATED_IO;
    }

    @Override
    public JobExecutionResult execute(Job job, String workerId) throws Exception {
        return execute(job, workerId, null);
    }

    @Override
    public JobExecutionResult execute(Job job, String workerId, JobProgressListener progressListener) throws Exception {
        int durationMs = 120;
        String payload = job.getPayload();
        if (payload != null && !payload.isBlank()) {
            try {
                if (payload.trim().startsWith("{")) {
                    JsonNode node = objectMapper.readTree(payload);
                    if (node.has("durationMs")) {
                        durationMs = Math.min(5000, Math.max(10, node.get("durationMs").asInt(120)));
                    } else if (node.has("simulatedDurationMs")) {
                        durationMs = Math.min(5000, Math.max(10, node.get("simulatedDurationMs").asInt(120)));
                    }
                }
            } catch (Exception ignored) {}
        }

        if (progressListener != null) {
            progressListener.onProgress(10, "Establishing socket / HTTP channel");
        }

        long startTime = System.currentTimeMillis();
        long deadline = startTime + durationMs;
        while (System.currentTimeMillis() < deadline) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Worker thread interrupted during SIMULATED_IO");
            }
            long remaining = deadline - System.currentTimeMillis();
            Thread.sleep(Math.min(50, Math.max(1, remaining)));

            if (progressListener != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                int pct = Math.min(95, (int) ((elapsed * 100) / Math.max(1, durationMs)));
                progressListener.onProgress(pct, "Transferring payloads (" + elapsed + "ms / " + durationMs + "ms)");
            }
        }

        if (progressListener != null) {
            progressListener.onProgress(100, "HTTP 200 OK received & connection closed");
        }

        String output = String.format("Simulated external I/O completed in %d ms (simulated HTTP 200 OK)", durationMs);
        return JobExecutionResult.success("I/O simulation succeeded", output);
    }
}
