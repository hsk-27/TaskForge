package com.taskforge.dto.response;

import java.util.List;

public record BatchJobResponse(
        int submittedCount,
        int rejectedCount,
        List<String> jobIds,
        String message
) {}
