package com.taskforge.dto.response;

/**
 * Interactive action card that the chatbot surfaces in chat
 * allowing the user to trigger real-time platform actions with a single click.
 */
public record ChatAction(
        String id,
        String label,
        String description,
        String endpoint,
        String httpMethod
) {}
