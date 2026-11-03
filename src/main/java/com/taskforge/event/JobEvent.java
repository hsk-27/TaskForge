package com.taskforge.event;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * An immutable record of an execution event within TaskForge.
 */
public record JobEvent(
        Instant timestamp,
        String jobId,
        JobEventType type,
        String threadName,
        String details
) {
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public JobEvent {
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        Objects.requireNonNull(jobId, "Job ID cannot be null");
        Objects.requireNonNull(type, "JobEventType cannot be null");
        threadName = threadName != null ? threadName : Thread.currentThread().getName();
        details = details != null ? details : "";
    }

    public static JobEvent of(String jobId, JobEventType type, String details) {
        return new JobEvent(Instant.now(), jobId, type, Thread.currentThread().getName(), details);
    }

    public static JobEvent of(String jobId, JobEventType type, String threadName, String details) {
        return new JobEvent(Instant.now(), jobId, type, threadName, details);
    }

    public String formatLog() {
        return String.format("[%s] [%-20s] [%-10s] %-9s - %s",
                TIME_FORMATTER.format(timestamp),
                threadName,
                jobId,
                type,
                details);
    }
}
