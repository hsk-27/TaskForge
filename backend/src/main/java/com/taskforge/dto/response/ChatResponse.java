package com.taskforge.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Structured response returned by the TaskForge Assistant.
 */
public record ChatResponse(
        String reply,
        String persona,
        List<ChatAction> actions,
        Map<String, Object> systemSnapshot,
        Instant timestamp
) {}
