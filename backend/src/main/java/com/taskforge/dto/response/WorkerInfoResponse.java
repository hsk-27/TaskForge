package com.taskforge.dto.response;

public record WorkerInfoResponse(
        String workerId,
        String state,
        String currentJobId,
        String currentJobName,
        Integer attemptNumber,
        Long elapsedMs,
        Integer progressPercent,
        String currentStage,
        Long totalProcessed,
        Long totalFailed,
        Double avgDurationMs
) {
    public WorkerInfoResponse(String workerId, String state, String currentJobId, String currentJobName, Integer attemptNumber, Long elapsedMs) {
        this(workerId, state, currentJobId, currentJobName, attemptNumber, elapsedMs, 0, "", 0L, 0L, 0.0);
    }
}
