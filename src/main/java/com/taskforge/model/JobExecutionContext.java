package com.taskforge.model;

import java.time.Instant;

/**
 * Provides execution context information to a running JobAction.
 */
public record JobExecutionContext(
        String jobId,
        int attemptNumber,
        String workerThreadName,
        Instant startedAt
) {}
