package com.taskforge.engine;

import com.taskforge.domain.Job;
import com.taskforge.domain.JobStatus;
import com.taskforge.domain.JobType;
import com.taskforge.dto.request.BatchJobRequest;
import com.taskforge.dto.response.BatchJobResponse;
import com.taskforge.repository.JobAttemptRepository;
import com.taskforge.repository.JobEventRepository;
import com.taskforge.repository.JobRepository;
import com.taskforge.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Integration Test for DAG Sequential Pipelines,
 * Cascading Aborts, Transitive Dependency Healing, and Worker Synchronization.
 */
@SpringBootTest
class DagPipelineIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private ExecutionEngine executionEngine;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobAttemptRepository attemptRepository;

    @Autowired
    private JobEventRepository eventRepository;

    @BeforeEach
    void setUp() {
        executionEngine.setPaused(true);
        executionEngine.getQueue().clear();
        jobService.clearAllData();
        executionEngine.setPaused(false);
    }

    private void awaitCondition(long timeoutMs, java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not met within " + timeoutMs + "ms");
    }

    @Test
    @DisplayName("5-Step Sequential DAG Pipeline executes in strict sequence to full success")
    void testSequentialPipelineExecutesToSuccess() throws Exception {
        BatchJobRequest request = new BatchJobRequest(
                5,
                "etl-pipeline",
                JobType.COMPUTE,
                0,
                0.0,
                20,
                3,
                null,
                true // isSequential = true
        );

        BatchJobResponse response = jobService.createBatch(request);
        assertEquals(5, response.submittedCount());
        List<String> ids = response.jobIds();
        assertEquals(5, ids.size());

        // Wait for all 5 jobs to complete
        awaitCondition(15000, () -> {
            long completed = jobRepository.countByStatus(JobStatus.SUCCEEDED);
            return completed == 5;
        });

        // Verify each step in order
        for (int i = 0; i < 5; i++) {
            Job job = jobRepository.findById(ids.get(i)).orElseThrow();
            assertEquals(JobStatus.SUCCEEDED, job.getStatus(), "Step " + (i + 1) + " must succeed");
            assertEquals(1, job.getAttemptCount());
            assertNotNull(job.getWorkerId());
            assertNotNull(job.getExecutionDurationMs());
            if (i > 0) {
                // Verify payload points to parent
                String parentId = ids.get(i - 1);
                assertTrue(job.getPayload().contains(parentId), "Step " + (i + 1) + " must reference parent " + parentId);
            }
        }
    }

    @Test
    @DisplayName("Sequential DAG Pipeline cascades permanent failure to all downstream steps")
    void testSequentialPipelineCascadesFailure() throws Exception {
        // Step 1: succeeds
        Job step1 = new Job("Step 1", JobType.SIMULATED_IO, 0, "{\"durationMs\":10}", 1);
        jobRepository.save(step1);

        // Step 2: fails immediately (0 max attempts)
        Job step2 = new Job("Step 2", JobType.FAILURE_DEMO, 0,
                "{\"dependsOnJobId\":\"" + step1.getId() + "\",\"mode\":\"alwaysFail\"}", 1);
        jobRepository.save(step2);

        // Step 3: depends on step 2
        Job step3 = new Job("Step 3", JobType.REPORT_GENERATION, 0,
                "{\"dependsOnJobId\":\"" + step2.getId() + "\"}", 1);
        jobRepository.save(step3);

        // Step 4: depends on step 3
        Job step4 = new Job("Step 4", JobType.COMPUTE, 0,
                "{\"dependsOnJobId\":\"" + step3.getId() + "\"}", 1);
        jobRepository.save(step4);

        // Enqueue step 1
        executionEngine.enqueue(step1.getId(), 0);

        // Wait for step 1 to succeed, step 2 to fail, and steps 3 and 4 to cascade abort
        awaitCondition(10000, () -> {
            Job j4 = jobRepository.findById(step4.getId()).orElse(null);
            return j4 != null && j4.getStatus() == JobStatus.FAILED;
        });

        Job s1 = jobRepository.findById(step1.getId()).orElseThrow();
        Job s2 = jobRepository.findById(step2.getId()).orElseThrow();
        Job s3 = jobRepository.findById(step3.getId()).orElseThrow();
        Job s4 = jobRepository.findById(step4.getId()).orElseThrow();

        assertEquals(JobStatus.SUCCEEDED, s1.getStatus());
        assertEquals(JobStatus.FAILED, s2.getStatus());
        assertEquals(JobStatus.FAILED, s3.getStatus());
        assertEquals(JobStatus.FAILED, s4.getStatus());

        // Verify steps 3 and 4 were aborted due to dependency and never ran on worker
        assertEquals(0, s3.getAttemptCount());
        assertEquals(0, s4.getAttemptCount());
        assertTrue(s3.getLastErrorMessage().contains("Dependency failure"));
        assertTrue(s4.getLastErrorMessage().contains("Dependency failure") || s4.getLastErrorMessage().contains("Prerequisite step"));
    }

    @Test
    @DisplayName("Operator cancellation of parent step cascades cancellation down the pipeline")
    void testCancelCascadesDownstream() throws Exception {
        executionEngine.setPaused(true);

        BatchJobRequest request = new BatchJobRequest(3, "cancel-dag", JobType.COMPUTE, 0, 0.0, 50, 3, null, true);
        BatchJobResponse response = jobService.createBatch(request);
        List<String> ids = response.jobIds();

        // Cancel step 1
        jobService.cancelJob(ids.get(0));

        Job s1 = jobRepository.findById(ids.get(0)).orElseThrow();
        Job s2 = jobRepository.findById(ids.get(1)).orElseThrow();
        Job s3 = jobRepository.findById(ids.get(2)).orElseThrow();

        assertEquals(JobStatus.CANCELLED, s1.getStatus());
        assertEquals(JobStatus.CANCELLED, s2.getStatus());
        assertEquals(JobStatus.CANCELLED, s3.getStatus());

        assertEquals(0, executionEngine.getQueue().size());
    }

    @Test
    @DisplayName("Delayed job execution respects schedule and transitions cleanly")
    void testDelayedJobExecutesAfterDelay() throws Exception {
        // Create job with 1-second delay
        Job delayed = new Job("Delayed Task", JobType.COMPUTE, 0, "{\"limit\":100}", 3);
        delayed.setStatus(JobStatus.RETRY_WAIT);
        delayed.setScheduledExecutionAt(java.time.Instant.now().plusSeconds(1));
        jobRepository.save(delayed);

        assertEquals(JobStatus.RETRY_WAIT, delayed.getStatus());

        // Wait for scheduler to promote to QUEUED and worker to complete it
        awaitCondition(8000, () -> {
            Job j = jobRepository.findById(delayed.getId()).orElse(null);
            return j != null && j.getStatus() == JobStatus.SUCCEEDED;
        });

        Job finished = jobRepository.findById(delayed.getId()).orElseThrow();
        assertEquals(JobStatus.SUCCEEDED, finished.getStatus());
        assertEquals(1, finished.getAttemptCount());
    }
}
