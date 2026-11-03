package com.taskforge.dto.response;

import java.util.List;

public record EngineStatusResponse(
        boolean isRunning,
        boolean isPaused,
        boolean ecoMode,
        int workerCount,
        int activeWorkers,
        int queueSize,
        int queueCapacity,
        long jvmUsedMemoryMb,
        long jvmMaxMemoryMb,
        int availableProcessors,
        List<WorkerInfoResponse> workers
) {}
