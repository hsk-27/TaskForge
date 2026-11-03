package com.taskforge.dto.response;

import com.taskforge.domain.Job;
import com.taskforge.domain.JobStatus;
import com.taskforge.domain.JobType;

import java.time.Instant;

public record JobResponse(
        String id,
        String name,
        JobType jobType,
        JobStatus status,
        int priority,
        String payload,
        Instant createdAt,
        Instant scheduledExecutionAt,
        Instant startedAt,
        Instant completedAt,
        int attemptCount,
        int maxAttempts,
        String lastErrorMessage,
        String workerId,
        Long executionDurationMs,
        Long queueWaitDurationMs,
        Long version
) {
    public static JobResponse fromEntity(Job job) {
        if (job == null) return null;
        return new JobResponse(
                job.getId(),
                job.getName(),
                job.getJobType(),
                job.getStatus(),
                job.getPriority(),
                job.getPayload(),
                job.getCreatedAt(),
                job.getScheduledExecutionAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getAttemptCount(),
                job.getMaxAttempts(),
                job.getLastErrorMessage(),
                job.getWorkerId(),
                job.getExecutionDurationMs(),
                job.getQueueWaitDurationMs(),
                job.getVersion()
        );
    }
}
