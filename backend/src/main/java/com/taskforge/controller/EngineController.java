package com.taskforge.controller;

import com.taskforge.dto.request.UpdateSettingsRequest;
import com.taskforge.dto.response.EngineStatusResponse;
import com.taskforge.service.EngineService;
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

@RestController
@RequestMapping("/api/v1/engine")
@Tag(name = "Engine", description = "Operations for monitoring and configuring the execution engine")
public class EngineController {

    private final EngineService engineService;

    public EngineController(EngineService engineService) {
        this.engineService = engineService;
    }

    @GetMapping("/status")
    @Operation(summary = "Get engine state, worker pool telemetry, and queue metrics")
    public ResponseEntity<EngineStatusResponse> getStatus() {
        return ResponseEntity.ok(engineService.getStatus());
    }

    @PostMapping("/settings")
    @Operation(summary = "Dynamically update worker count, queue capacity, or backoff parameters")
    public ResponseEntity<EngineStatusResponse> updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        return ResponseEntity.ok(engineService.updateSettings(request));
    }

    @PostMapping("/pause")
    @Operation(summary = "Pause worker processing of new queued jobs")
    public ResponseEntity<EngineStatusResponse> pause() {
        return ResponseEntity.ok(engineService.pause());
    }

    @PostMapping("/resume")
    @Operation(summary = "Resume worker processing of queued jobs")
    public ResponseEntity<EngineStatusResponse> resume() {
        return ResponseEntity.ok(engineService.resume());
    }

    @PostMapping("/eco")
    @Operation(summary = "Toggle PC-Safe Eco-Mode for gentle CPU scheduling and thread throttling")
    public ResponseEntity<EngineStatusResponse> setEcoMode(@RequestParam(defaultValue = "true") boolean enabled) {
        return ResponseEntity.ok(engineService.setEcoMode(enabled));
    }
}
