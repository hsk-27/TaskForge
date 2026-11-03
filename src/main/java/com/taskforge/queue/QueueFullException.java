package com.taskforge.queue;

/**
 * Thrown when attempting to submit a job to a bounded queue that is at maximum capacity.
 */
public class QueueFullException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int capacity;

    public QueueFullException(String message, int capacity) {
        super(message);
        this.capacity = capacity;
    }

    public int getCapacity() {
        return capacity;
    }
}
