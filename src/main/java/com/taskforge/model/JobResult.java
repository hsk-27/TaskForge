package com.taskforge.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Encapsulates the execution result of a Job.
 * Immutable to guarantee safe sharing across worker and monitoring threads.
 */
public final class JobResult {
    private final boolean success;
    private final String message;
    private final Throwable error;

    private JobResult(boolean success, String message, Throwable error) {
        this.success = success;
        this.message = message != null ? message : "";
        this.error = error;
    }

    public static JobResult success(String message) {
        return new JobResult(true, message, null);
    }

    public static JobResult failure(String message, Throwable error) {
        return new JobResult(false, message, error);
    }

    public static JobResult failure(String message) {
        return new JobResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JobResult that)) return false;
        return success == that.success &&
                Objects.equals(message, that.message) &&
                Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, error);
    }

    @Override
    public String toString() {
        return "JobResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                (error != null ? ", error=" + error.getMessage() : "") +
                '}';
    }
}
