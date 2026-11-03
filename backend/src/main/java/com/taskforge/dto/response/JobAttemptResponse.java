package com.taskforge.dto.response;

import com.taskforge.domain.JobAttempt;

import java.time.Instant;

public record JobAttemptResponse(
        String id,
        String jobId,
        int attemptNumber,
        String workerId,
        Instant startedAt,
        Instant completedAt,
        String outcome,
        String errorMessage,
        Long durationMs
) {
    public static JobAttemptResponse fromEntity(JobAttempt attempt) {
        if (attempt == null) return null;
        return new JobAttemptResponse(
                attempt.getId(),
                attempt.getJobId(),
                attempt.getAttemptNumber(),
                attempt.getWorkerId(),
                attempt.getStartedAt(),
                attempt.getCompletedAt(),
                attempt.getOutcome(),
                attempt.getErrorMessage(),
                attempt.getDurationMs()
        );
    }
}
