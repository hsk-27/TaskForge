package com.taskforge.handler;

public record JobExecutionResult(
        boolean success,
        String message,
        String output,
        String errorMessage
) {
    public static JobExecutionResult success(String message, String output) {
        return new JobExecutionResult(true, message, output, null);
    }

    public static JobExecutionResult failure(String errorMessage) {
        return new JobExecutionResult(false, null, null, errorMessage);
    }
}
