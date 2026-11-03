package com.taskforge.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.taskforge.domain.JobType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record BatchJobRequest(
        @Min(value = 1, message = "Batch count must be at least 1")
        @Max(value = 500, message = "Batch count cannot exceed 500")
        int count,

        String prefix,

        JobType jobType,

        Integer priority,

        @DecimalMin(value = "0.0", message = "Failure rate must be between 0.0 and 1.0")
        @DecimalMax(value = "1.0", message = "Failure rate must be between 0.0 and 1.0")
        Double failureRate,

        @JsonAlias({"durationMs", "duration"})
        @Min(value = 0, message = "Simulated duration cannot be negative")
        @Max(value = 10000, message = "Simulated duration cannot exceed 10000ms")
        Integer simulatedDurationMs,

        @Min(value = 1, message = "Max attempts must be at least 1")
        @Max(value = 10, message = "Max attempts cannot exceed 10")
        Integer maxAttempts,

        String payload,

        Boolean isSequential
) {
    public BatchJobRequest(int count, String prefix, JobType jobType, Integer priority, Double failureRate, Integer simulatedDurationMs, Integer maxAttempts) {
        this(count, prefix, jobType, priority, failureRate, simulatedDurationMs, maxAttempts, null, false);
    }

    public BatchJobRequest(int count, String prefix, JobType jobType, Integer priority, Double failureRate, Integer simulatedDurationMs, Integer maxAttempts, String payload) {
        this(count, prefix, jobType, priority, failureRate, simulatedDurationMs, maxAttempts, payload, false);
    }

    public String resolvedPrefix() {
        return prefix != null && !prefix.isBlank() ? prefix : "batch-task";
    }

    public double resolvedFailureRate() {
        return failureRate != null ? failureRate : 0.0;
    }

    public int resolvedDurationMs() {
        return simulatedDurationMs != null ? simulatedDurationMs : 100;
    }

    public int resolvedMaxAttempts() {
        return maxAttempts != null ? maxAttempts : 3;
    }

    public boolean resolvedIsSequential() {
        return isSequential != null && isSequential;
    }
}
