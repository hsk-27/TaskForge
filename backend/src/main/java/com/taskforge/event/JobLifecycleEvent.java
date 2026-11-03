package com.taskforge.event;

import com.taskforge.domain.JobStatus;

import java.time.Instant;

public record JobLifecycleEvent(
        String jobId,
        String jobName,
        String eventType,
        JobStatus status,
        String message,
        String workerId,
        Instant timestamp
) {}
