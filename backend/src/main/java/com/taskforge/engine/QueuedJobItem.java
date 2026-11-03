package com.taskforge.engine;

import java.util.Objects;

/**
 * Encapsulates a job item waiting in the execution queue with priority and FIFO tie-breaking.
 */
public class QueuedJobItem implements Comparable<QueuedJobItem> {
    private final String jobId;
    private final int priority;
    private final long sequenceNumber;

    public QueuedJobItem(String jobId, int priority, long sequenceNumber) {
        this.jobId = Objects.requireNonNull(jobId, "jobId cannot be null");
        this.priority = priority;
        this.sequenceNumber = sequenceNumber;
    }

    public String getJobId() {
        return jobId;
    }

    public int getPriority() {
        return priority;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public int compareTo(QueuedJobItem other) {
        // Higher priority jobs are dequeued first
        int cmp = Integer.compare(other.priority, this.priority);
        if (cmp != 0) {
            return cmp;
        }
        // Strict FIFO ordering for equal priorities
        return Long.compare(this.sequenceNumber, other.sequenceNumber);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueuedJobItem that = (QueuedJobItem) o;
        return Objects.equals(jobId, that.jobId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId);
    }

    @Override
    public String toString() {
        return "QueuedJobItem{" +
                "jobId='" + jobId + '\'' +
                ", priority=" + priority +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}
