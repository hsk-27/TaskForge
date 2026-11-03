package com.taskforge.dto.response;

import com.taskforge.domain.JobEvent;

import java.time.Instant;

public record JobEventResponse(
        Long id,
        String jobId,
        String eventType,
        String message,
        String workerId,
        Instant createdAt
) {
    public static JobEventResponse fromEntity(JobEvent event) {
        if (event == null) return null;
        return new JobEventResponse(
                event.getId(),
                event.getJobId(),
                event.getEventType(),
                event.getMessage(),
                event.getWorkerId(),
                event.getCreatedAt()
        );
    }
}
