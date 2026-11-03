package com.taskforge.service;

import com.taskforge.config.EngineProperties;
import com.taskforge.dto.request.UpdateSettingsRequest;
import com.taskforge.dto.response.EngineStatusResponse;
import com.taskforge.engine.ExecutionEngine;
import org.springframework.stereotype.Service;

@Service
public class EngineService {

    private final ExecutionEngine engine;
    private final EngineProperties properties;

    public EngineService(ExecutionEngine engine, EngineProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    public EngineStatusResponse getStatus() {
        long totalMem = Runtime.getRuntime().totalMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        long maxMem = Runtime.getRuntime().maxMemory();
        long usedMb = (totalMem - freeMem) / (1024 * 1024);
        long maxMb = maxMem / (1024 * 1024);
        int cpus = Runtime.getRuntime().availableProcessors();

        return new EngineStatusResponse(
                engine.isRunning(),
                engine.isPaused(),
                engine.isEcoMode(),
                properties.getWorkerCount(),
                engine.getActiveWorkerCount(),
                engine.getQueue().size(),
                engine.getQueue().getCapacity(),
                usedMb,
                maxMb,
                cpus,
                engine.getWorkerSnapshots()
        );
    }

    public EngineStatusResponse updateSettings(UpdateSettingsRequest request) {
        if (request.workerCount() != null) {
            engine.adjustWorkerCount(request.workerCount());
        }
        if (request.queueCapacity() != null) {
            engine.updateQueueCapacity(request.queueCapacity());
        }
        if (request.maxAttempts() != null) {
            properties.setMaxAttempts(request.maxAttempts());
        }
        if (request.baseBackoffMs() != null) {
            properties.setBaseBackoffMs(request.baseBackoffMs());
        }
        return getStatus();
    }

    public EngineStatusResponse setEcoMode(boolean enabled) {
        engine.setEcoMode(enabled);
        return getStatus();
    }

    public EngineStatusResponse pause() {
        engine.setPaused(true);
        return getStatus();
    }

    public EngineStatusResponse resume() {
        engine.setPaused(false);
        return getStatus();
    }
}
