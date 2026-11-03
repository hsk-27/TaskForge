package com.taskforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "taskforge.engine")
public class EngineProperties {
    private int workerCount = 2;
    private int queueCapacity = 100;
    private boolean ecoMode = true;
    private int maxAttempts = 3;
    private long baseBackoffMs = 1000;
    private long maxBackoffMs = 30000;
    private double jitterFactor = 0.1;
    private int drainTimeoutSeconds = 15;
    private int staleJobTimeoutMinutes = 5;
    private long pollIntervalMs = 200;

    public int getWorkerCount() {
        return workerCount;
    }

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public boolean isEcoMode() {
        return ecoMode;
    }

    public void setEcoMode(boolean ecoMode) {
        this.ecoMode = ecoMode;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getBaseBackoffMs() {
        return baseBackoffMs;
    }

    public void setBaseBackoffMs(long baseBackoffMs) {
        this.baseBackoffMs = baseBackoffMs;
    }

    public long getMaxBackoffMs() {
        return maxBackoffMs;
    }

    public void setMaxBackoffMs(long maxBackoffMs) {
        this.maxBackoffMs = maxBackoffMs;
    }

    public double getJitterFactor() {
        return jitterFactor;
    }

    public void setJitterFactor(double jitterFactor) {
        this.jitterFactor = jitterFactor;
    }

    public int getDrainTimeoutSeconds() {
        return drainTimeoutSeconds;
    }

    public void setDrainTimeoutSeconds(int drainTimeoutSeconds) {
        this.drainTimeoutSeconds = drainTimeoutSeconds;
    }

    public int getStaleJobTimeoutMinutes() {
        return staleJobTimeoutMinutes;
    }

    public void setStaleJobTimeoutMinutes(int staleJobTimeoutMinutes) {
        this.staleJobTimeoutMinutes = staleJobTimeoutMinutes;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }
}
