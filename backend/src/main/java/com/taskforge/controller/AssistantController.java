package com.taskforge.controller;

import com.taskforge.dto.request.ChatRequest;
import com.taskforge.dto.response.ChatResponse;
import com.taskforge.service.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Assistant", description = "End-to-end intelligent assistant for viva defense, operator actions, and developer queries")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    @Operation(summary = "Submit a natural language query or operational directive to the assistant")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = assistantService.processQuery(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Get suggested prompt questions based on user persona")
    public ResponseEntity<List<String>> getSuggestions(@RequestParam(defaultValue = "DEVELOPER") String persona) {
        String cleanPersona = persona != null ? persona.trim().toUpperCase() : "DEVELOPER";
        List<String> suggestions = switch (cleanPersona) {
            case "STUDENT" -> List.of(
                    "Explain the producer-consumer pattern in JobQueue",
                    "Why is task execution outside @Transactional?",
                    "Explain the exponential backoff formula and jitter",
                    "Why Server-Sent Events instead of WebSockets?",
                    "How does crash recovery work on JVM reboot?"
            );
            case "OPERATOR" -> List.of(
                    "Show cluster health and worker pool load",
                    "Show Dead Letter Queue (DLQ) errors",
                    "Replay all failed jobs in DLQ",
                    "Cancel all pending queued tasks",
                    "Dispatch 5-job smoke test"
            );
            case "NOVICE" -> List.of(
                    "What is TaskForge in simple words?",
                    "Is the system healthy right now?",
                    "Can I run a quick test?",
                    "What happens when a task fails?"
            );
            default -> List.of(
                    "Show REST API endpoints with curl examples",
                    "Show JSON payload schemas for single and batch jobs",
                    "How does the bounded JobQueue enforce backpressure?",
                    "Submit 5 test compute jobs",
                    "Explain cooperative thread interruption in handlers"
            );
        };
        return ResponseEntity.ok(suggestions);
    }
}
