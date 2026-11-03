package com.taskforge.domain;

/**
 * Supported demonstration job workloads in TaskForge.
 */
public enum JobType {
    /**
     * CPU-intensive computations (e.g. prime numbers, matrix ops, Fibonacci).
     */
    COMPUTE,

    /**
     * Aggregation and filtering over synthetic data records.
     */
    DATA_PROCESSING,

    /**
     * Structured report generation with summaries and markdown/JSON synthesis.
     */
    REPORT_GENERATION,

    /**
     * Simulated network/disk latency (external service call simulation).
     */
    SIMULATED_IO,

    /**
     * Controlled failure injection for testing retry policies and backoff.
     */
    FAILURE_DEMO
}
