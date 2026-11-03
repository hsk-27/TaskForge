package com.taskforge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskforge.domain.Job;
import com.taskforge.domain.JobStatus;
import com.taskforge.domain.JobType;
import com.taskforge.dto.request.BatchJobRequest;
import com.taskforge.dto.request.CreateJobRequest;
import com.taskforge.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class JobControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private com.taskforge.service.JobService jobService;

    @Autowired
    private com.taskforge.engine.ExecutionEngine executionEngine;

    @BeforeEach
    void setUp() {
        executionEngine.setPaused(true);
        executionEngine.getQueue().clear();
        jobService.clearAllData();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        executionEngine.setPaused(false);
    }

    @Test
    void testCreateJobSuccess() throws Exception {
        CreateJobRequest request = new CreateJobRequest("Test Prime Task", JobType.COMPUTE, 1, "{\"limit\": 1000}", 3);

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Test Prime Task"))
                .andExpect(jsonPath("$.jobType").value("COMPUTE"))
                .andExpect(jsonPath("$.priority").value(1));

        assertEquals(1, jobRepository.count());
    }

    @Test
    void testCreateJobValidationFailure() throws Exception {
        // Blank name should fail validation
        CreateJobRequest request = new CreateJobRequest("", JobType.COMPUTE, 0, null, 3);

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isNotEmpty());
    }

    @Test
    void testBatchSubmitAndExecution() throws Exception {
        BatchJobRequest request = new BatchJobRequest(10, "test-batch", JobType.SIMULATED_IO, 0, 0.0, 50, 3);

        mockMvc.perform(post("/api/v1/jobs/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.submittedCount").value(10))
                .andExpect(jsonPath("$.jobIds", hasSize(10)));

        assertEquals(10, jobRepository.count());
    }

    @Test
    void testListJobsWithFilters() throws Exception {
        Job job = new Job("Searchable Job", JobType.DATA_PROCESSING, 0, null, 3);
        jobRepository.save(job);

        mockMvc.perform(get("/api/v1/jobs")
                        .param("search", "Searchable")
                        .param("jobType", "DATA_PROCESSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Searchable Job"));
    }

    @Test
    void testEngineStatusEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/engine/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRunning").value(true))
                .andExpect(jsonPath("$.workerCount").isNumber())
                .andExpect(jsonPath("$.queueCapacity").isNumber());
    }

    @Test
    void testMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").isNumber())
                .andExpect(jsonPath("$.successRatePercent").isNumber());
    }

    @Test
    void testCancelJob() throws Exception {
        Job job = new Job("To Cancel", JobType.SIMULATED_IO, 0, null, 3);
        jobRepository.save(job);

        mockMvc.perform(post("/api/v1/jobs/" + job.getId() + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void testDlqAndReplay() throws Exception {
        Job failedJob = new Job("Dead Letter Job", JobType.SIMULATED_IO, 0, null, 3);
        failedJob.markRunning("worker-test", java.time.Instant.now());
        failedJob.markFailed("Fatal database crash simulated", java.time.Instant.now());
        jobRepository.save(failedJob);

        // Verify DLQ list returns the failed job
        mockMvc.perform(get("/api/v1/jobs/dlq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Dead Letter Job"))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"));

        // Verify Replay DLQ recovers the job
        mockMvc.perform(post("/api/v1/jobs/dlq/replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayedCount").value(1));

        Job reloaded = jobRepository.findById(failedJob.getId()).orElseThrow();
        assertEquals(JobStatus.QUEUED, reloaded.getStatus());
    }

    @Test
    void testExportDiagnostics() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportedAt").isNotEmpty())
                .andExpect(jsonPath("$.system.availableProcessors").isNumber())
                .andExpect(jsonPath("$.engine.isRunning").value(true))
                .andExpect(jsonPath("$.statusCounts").isMap())
                .andExpect(jsonPath("$.jobs").isArray());
    }

    @Test
    void testEcoModeEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/engine/eco").param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ecoMode").value(false));

        mockMvc.perform(post("/api/v1/engine/eco").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ecoMode").value(true));
    }

    @Test
    void testCreateDelayedJob() throws Exception {
        CreateJobRequest request = new CreateJobRequest("Delayed Backup", JobType.REPORT_GENERATION, 5, "{}", 3, 30);

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RETRY_WAIT"))
                .andExpect(jsonPath("$.scheduledExecutionAt").isNotEmpty());

        Job job = jobRepository.findAll().get(0);
        assertEquals(JobStatus.RETRY_WAIT, job.getStatus());
        // Verify not placed in in-memory queue until time elapses
        assertEquals(0, executionEngine.getQueue().size());
    }

    @Test
    void testBulkCancelAndPurge() throws Exception {
        Job queuedJob = new Job("Pending Task", JobType.COMPUTE, 0, null, 3);
        jobRepository.save(queuedJob);

        Job terminalJob = new Job("Done Task", JobType.COMPUTE, 0, null, 3);
        terminalJob.markRunning("w1", null);
        terminalJob.markSucceeded(null);
        jobRepository.save(terminalJob);

        // Cancel all pending
        mockMvc.perform(post("/api/v1/jobs/bulk/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledCount").value(1));

        Job reloadedPending = jobRepository.findById(queuedJob.getId()).orElseThrow();
        assertEquals(JobStatus.CANCELLED, reloadedPending.getStatus());

        // Purge terminal
        mockMvc.perform(delete("/api/v1/jobs/bulk/terminal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purgedCount").value(2));

        assertEquals(0, jobRepository.count());
    }
}
