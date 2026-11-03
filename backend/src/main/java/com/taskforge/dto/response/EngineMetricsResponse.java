package com.taskforge.dto.response;

import java.util.List;
import java.util.Map;

public record EngineMetricsResponse(
        long totalJobs,
        long queuedJobs,
        long runningJobs,
        long retryWaitJobs,
        long succeededJobs,
        long failedJobs,
        long cancelledJobs,
        double successRatePercent,
        double failureRatePercent,
        double averageExecutionDurationMs,
        double averageQueueWaitDurationMs,
        double throughputPerMinute,
        List<Map<String, Object>> topFailureReasons
) {}
