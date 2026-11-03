package com.taskforge.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateSettingsRequest(
        @Min(value = 1, message = "Worker count must be at least 1")
        @Max(value = 32, message = "Worker count cannot exceed 32")
        Integer workerCount,

        @Min(value = 5, message = "Queue capacity must be at least 5")
        @Max(value = 1000, message = "Queue capacity cannot exceed 1000")
        Integer queueCapacity,

        @Min(value = 1, message = "Max attempts must be at least 1")
        @Max(value = 10, message = "Max attempts cannot exceed 10")
        Integer maxAttempts,

        @Min(value = 100, message = "Base backoff must be at least 100ms")
        @Max(value = 60000, message = "Base backoff cannot exceed 60000ms")
        Long baseBackoffMs
) {}
