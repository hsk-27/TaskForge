package com.taskforge.exception;

public class EngineShuttingDownException extends RuntimeException {
    public EngineShuttingDownException(String message) {
        super(message);
    }
}
