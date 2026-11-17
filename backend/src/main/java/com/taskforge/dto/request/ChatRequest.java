package com.taskforge.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for submitting a query to the TaskForge Intelligent Assistant.
 *
 * @param message The user's query or command
 * @param persona The user persona / mode: "SRE" (Root cause analysis & incident triage),
 *                "OPERATOR" (Fleet health diagnostics & cluster actions),
 *                "DEVELOPER" (REST API, curl & SDK integration),
 *                or "AUDITOR" (SLA latency, compliance & transaction boundaries)
 * @param actionId Optional directive ID to execute directly (e.g. "REPLAY_DLQ", "SUBMIT_SAMPLE", "CANCEL_PENDING")
 */
public record ChatRequest(
        @NotBlank(message = "Message cannot be blank")
        String message,
        String persona,
        String actionId
) {
    public String resolvedPersona() {
        if (persona == null || persona.isBlank()) {
            return "DEVELOPER";
        }
        return persona.trim().toUpperCase();
    }
}
