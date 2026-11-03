package com.taskforge.controller;

import com.taskforge.dto.response.JobEventResponse;
import com.taskforge.event.SseEventBroadcaster;
import com.taskforge.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events", description = "Operations for real-time Server-Sent Events (SSE) and event auditing")
public class EventStreamController {

    private final SseEventBroadcaster sseBroadcaster;
    private final JobService jobService;

    public EventStreamController(SseEventBroadcaster sseBroadcaster, JobService jobService) {
        this.sseBroadcaster = sseBroadcaster;
        this.jobService = jobService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to live real-time execution events via Server-Sent Events (SSE)")
    public SseEmitter streamEvents() {
        return sseBroadcaster.registerClient();
    }

    @GetMapping("/recent")
    @Operation(summary = "Get the most recent 50 execution events")
    public ResponseEntity<List<JobEventResponse>> getRecentEvents() {
        return ResponseEntity.ok(jobService.getRecentEvents());
    }
}
