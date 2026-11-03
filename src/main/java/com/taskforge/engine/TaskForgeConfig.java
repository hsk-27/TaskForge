package com.taskforge.engine;

/**
 * Immutable configuration for the TaskForge execution engine.
 */
public record TaskForgeConfig(
        int workerCount,
        int queueCapacity
) {
    public static final int DEFAULT_WORKER_COUNT = 4;
    public static final int DEFAULT_QUEUE_CAPACITY = 100;

    public TaskForgeConfig {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive, got " + workerCount);
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive, got " + queueCapacity);
        }
    }

    public static TaskForgeConfig defaultConfig() {
        return new TaskForgeConfig(DEFAULT_WORKER_COUNT, DEFAULT_QUEUE_CAPACITY);
    }

    public static TaskForgeConfig of(int workerCount, int queueCapacity) {
        return new TaskForgeConfig(workerCount, queueCapacity);
    }
}
