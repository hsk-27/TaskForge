package com.taskforge.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for submitting a query to the TaskForge Intelligent Assistant.
 *
 * @param message The user's query or command
 * @param persona The user persona / mode: "STUDENT" (Viva prep & CS theory),
 *                "OPERATOR" (DevOps, health diagnostics & actions),
 *                "DEVELOPER" (REST API, curl & internals),
 *                or "NOVICE" (Plain English & analogies)
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
