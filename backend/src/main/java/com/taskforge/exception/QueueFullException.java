package com.taskforge.exception;

public class QueueFullException extends RuntimeException {
    private final int capacity;

    public QueueFullException(String message, int capacity) {
        super(message);
        this.capacity = capacity;
    }

    public int getCapacity() {
        return capacity;
    }
}
