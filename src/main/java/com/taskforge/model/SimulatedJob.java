package com.taskforge.model;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory and builder for creating simulated jobs with deterministic behavior.
 * Useful for automated testing, latency modeling, and systems demonstrations.
 */
public final class SimulatedJob {

    private SimulatedJob() {}

    /**
     * Creates a job that sleeps for the simulated duration and succeeds.
     */
    public static Job success(String id, String name, Duration duration, String message) {
        return new Job(id, name, ctx -> {
            simulateWork(duration);
            return JobResult.success(message != null ? message : "Simulated work completed successfully");
        });
    }

    /**
     * Creates a job that sleeps for the simulated duration and reports a deterministic failure.
     */
    public static Job failure(String id, String name, Duration duration, String errorMessage) {
        return new Job(id, name, ctx -> {
            simulateWork(duration);
            return JobResult.failure(errorMessage != null ? errorMessage : "Simulated deterministic failure");
        });
    }

    /**
     * Creates a job that sleeps for the simulated duration and throws an unhandled exception.
     */
    public static Job exceptional(String id, String name, Duration duration, Exception exception) {
        return new Job(id, name, ctx -> {
            simulateWork(duration);
            throw exception != null ? exception : new RuntimeException("Simulated catastrophic crash");
        });
    }

    /**
     * Creates a job that fails for the first {@code failAttempts} tries, then succeeds on subsequent attempts.
     */
    public static Job failThenSucceed(String id, String name, Duration duration, int failAttempts, String successMsg) {
        AtomicInteger attempts = new AtomicInteger(0);
        return new Job(id, name, ctx -> {
            simulateWork(duration);
            int currentAttempt = attempts.incrementAndGet();
            if (currentAttempt <= failAttempts) {
                return JobResult.failure("Deterministic failure on attempt #" + currentAttempt + " (target success after " + failAttempts + ")");
            }
            return JobResult.success(successMsg != null ? successMsg : "Succeeded on attempt #" + currentAttempt);
        });
    }

    private static void simulateWork(Duration duration) throws InterruptedException {
        if (duration != null && !duration.isZero() && !duration.isNegative()) {
            Thread.sleep(duration.toMillis());
        }
    }
}
