package com.taskforge.dto.request;

import com.taskforge.domain.JobType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateJobRequest(
        @NotBlank(message = "Job name is required")
        @Size(max = 255, message = "Job name cannot exceed 255 characters")
        String name,

        @NotNull(message = "Job type is required")
        JobType jobType,

        @Min(value = -10, message = "Priority must be between -10 and 10")
        @Max(value = 10, message = "Priority must be between -10 and 10")
        Integer priority,

        String payload,

        @Min(value = 1, message = "Max attempts must be at least 1")
        @Max(value = 10, message = "Max attempts cannot exceed 10")
        Integer maxAttempts,

        @Min(value = 0, message = "Delay seconds cannot be negative")
        @Max(value = 86400, message = "Delay seconds cannot exceed 86400 (24h)")
        Integer delaySeconds
) {
    public CreateJobRequest(String name, JobType jobType, Integer priority, String payload, Integer maxAttempts) {
        this(name, jobType, priority, payload, maxAttempts, 0);
    }

    public int resolvedPriority() {
        return priority != null ? priority : 0;
    }

    public int resolvedMaxAttempts() {
        return maxAttempts != null ? maxAttempts : 3;
    }

    public int resolvedDelaySeconds() {
        return delaySeconds != null ? delaySeconds : 0;
    }
}
