package com.taskforge.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real-Time Telemetry Streaming via Server-Sent Events (SSE)
 *
 * Architectural Evaluation: SSE vs WebSockets vs Short Polling
 * 1. Over Short Polling: Polling sends repeated HTTP requests, causing connection overhead
 *    and unnecessary database amplification.
 * 2. Over WebSockets: For a developer monitoring console, communication is strictly server-to-client
 *    (unidirectional telemetry push). SSE operates over standard HTTP/1.1 and HTTP/2, natively traverses
 *    corporate proxies and API gateways without custom protocol upgrades, and browsers natively handle
 *    reconnection via the EventSource API.
 *
 * Concurrency Note:
 * Uses CopyOnWriteArrayList so multiple worker threads can broadcast events concurrently
 * without locking, avoiding ConcurrentModificationException when clients connect or disconnect.
 */
@Component
@EnableScheduling
public class SseEventBroadcaster {
    private static final Logger log = LoggerFactory.getLogger(SseEventBroadcaster.class);
    private static final Long SSE_TIMEOUT = 180_000L; // 3 minutes timeout

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter registerClient() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError((ex) -> emitters.remove(emitter));

        try {
            // Send initial connection handshake event
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("TaskForge Event Stream Connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void broadcast(JobLifecycleEvent event) {
        if (emitters.isEmpty()) return;

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("JOB_EVENT")
                        .data(event));
            } catch (Exception ex) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    public record JobProgressPayload(
            String jobId,
            String workerId,
            int progressPercent,
            String stage,
            long timestamp
    ) {}

    public void broadcastProgress(String jobId, String workerId, int progressPercent, String stage) {
        if (emitters.isEmpty()) return;

        JobProgressPayload payload = new JobProgressPayload(
                jobId,
                workerId,
                progressPercent,
                stage,
                System.currentTimeMillis()
        );

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("JOB_PROGRESS")
                        .data(payload));
            } catch (Exception ex) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    /**
     * Periodic heartbeat to prevent proxies and browsers from terminating idle SSE connections.
     */
    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) return;

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("HEARTBEAT")
                        .data(System.currentTimeMillis()));
            } catch (Exception ex) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    public int getConnectedClientsCount() {
        return emitters.size();
    }
}
