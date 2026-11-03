package com.taskforge.exception;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String id) {
        super("Job not found with id: " + id);
    }
}
