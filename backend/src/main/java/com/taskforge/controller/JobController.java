package com.taskforge.controller;

import com.taskforge.domain.JobStatus;
import com.taskforge.domain.JobType;
import com.taskforge.dto.request.BatchJobRequest;
import com.taskforge.dto.request.CreateJobRequest;
import com.taskforge.dto.response.BatchJobResponse;
import com.taskforge.dto.response.JobAttemptResponse;
import com.taskforge.dto.response.JobDagResponse;
import com.taskforge.dto.response.JobEventResponse;
import com.taskforge.dto.response.JobResponse;
import com.taskforge.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs", description = "Operations for creating, inspecting, cancelling, and retrying jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * REST Semantics: 201 Created
     * Submits a single background job. Returns 201 Created with the Job entity and its generated UUID.
     */
    @PostMapping
    @Operation(summary = "Submit a single job")
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
        JobResponse created = jobService.createJob(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * REST Semantics: 202 Accepted
     * Submits a batch of jobs for asynchronous processing.
     * Viva Note: We return 202 Accepted rather than 200 OK because the batch request has been
     * accepted for processing, but completion occurs asynchronously in background worker threads.
     */
    @PostMapping("/batch")
    @Operation(summary = "Submit a batch of jobs for concurrent execution")
    public ResponseEntity<BatchJobResponse> createBatch(@Valid @RequestBody BatchJobRequest request) {
        BatchJobResponse response = jobService.createBatch(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of(
            "createdAt", "name", "status", "jobType", "priority", "startedAt", "completedAt", "attemptCount"
    );

    @GetMapping
    @Operation(summary = "List jobs with pagination, sorting, and filtering")
    public ResponseEntity<Page<JobResponse>> listJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) JobType jobType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        String validSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        Sort.Direction validDirection = direction != null ? direction : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(validDirection, validSortBy));
        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        return ResponseEntity.ok(jobService.listJobs(status, jobType, cleanSearch, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed information for a specific job")
    public ResponseEntity<JobResponse> getJob(@PathVariable String id) {
        return ResponseEntity.ok(jobService.getJob(id));
    }

    @GetMapping("/{id}/dag")
    @Operation(summary = "Get DAG dependency hierarchy (prerequisites and downstream jobs) for a job")
    public ResponseEntity<JobDagResponse> getJobDag(@PathVariable String id) {
        return ResponseEntity.ok(jobService.getJobDag(id));
    }

    @GetMapping("/{id}/attempts")
    @Operation(summary = "Get execution attempt history for a specific job")
    public ResponseEntity<List<JobAttemptResponse>> getAttempts(@PathVariable String id) {
        return ResponseEntity.ok(jobService.getAttempts(id));
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Get lifecycle event log for a specific job")
    public ResponseEntity<List<JobEventResponse>> getEvents(@PathVariable String id) {
        return ResponseEntity.ok(jobService.getEvents(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an active or queued job")
    public ResponseEntity<JobResponse> cancelJob(@PathVariable String id) {
        return ResponseEntity.ok(jobService.cancelJob(id));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Manually retry a failed or cancelled job")
    public ResponseEntity<JobResponse> retryJob(@PathVariable String id) {
        return ResponseEntity.ok(jobService.retryJob(id));
    }

    @GetMapping("/dlq")
    @Operation(summary = "List permanently failed jobs (Dead Letter Queue)")
    public ResponseEntity<Page<JobResponse>> getDlq(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(jobService.getDlq(pageable));
    }

    @PostMapping("/dlq/replay")
    @Operation(summary = "Replay all permanently failed jobs from the Dead Letter Queue")
    public ResponseEntity<java.util.Map<String, Object>> replayDlq() {
        int count = jobService.replayDlq();
        return ResponseEntity.ok(java.util.Map.of(
                "replayedCount", count,
                "message", "Replayed " + count + " jobs from Dead Letter Queue"
        ));
    }

    @GetMapping("/export")
    @Operation(summary = "Export complete diagnostic snapshot with system telemetry and job records")
    public ResponseEntity<java.util.Map<String, Object>> exportDiagnostics() {
        return ResponseEntity.ok(jobService.exportDiagnostics());
    }

    @PostMapping("/bulk/cancel")
    @Operation(summary = "Cancel all pending queued and scheduled tasks")
    public ResponseEntity<java.util.Map<String, Object>> cancelAllPending() {
        int count = jobService.cancelAllPending();
        return ResponseEntity.ok(java.util.Map.of(
                "cancelledCount", count,
                "message", "Cancelled " + count + " pending tasks"
        ));
    }

    @DeleteMapping("/bulk/terminal")
    @Operation(summary = "Purge all completed terminal job records (succeeded, failed, cancelled)")
    public ResponseEntity<java.util.Map<String, Object>> purgeTerminal() {
        int count = jobService.purgeTerminalJobs();
        return ResponseEntity.ok(java.util.Map.of(
                "purgedCount", count,
                "message", "Purged " + count + " completed job records"
        ));
    }

    @DeleteMapping("/clear")
    @Operation(summary = "Clear all demo jobs, attempts, and event records")
    public ResponseEntity<Void> clearAll() {
        jobService.clearAllData();
        return ResponseEntity.noContent().build();
    }
}
