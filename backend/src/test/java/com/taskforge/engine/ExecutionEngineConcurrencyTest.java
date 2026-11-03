package com.taskforge.engine;

import com.taskforge.domain.Job;
import com.taskforge.domain.JobStatus;
import com.taskforge.domain.JobType;
import com.taskforge.repository.JobAttemptRepository;
import com.taskforge.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ExecutionEngineConcurrencyTest {

    @Autowired
    private ExecutionEngine executionEngine;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobAttemptRepository attemptRepository;

    @BeforeEach
    void setUp() {
        attemptRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
    }

    @Test
    void testConcurrentSubmissionsAndMultiWorkerExecution() throws Exception {
        int jobCount = 20;
        int submitterThreads = 4;
        ExecutorService submitterPool = Executors.newFixedThreadPool(submitterThreads);
        CountDownLatch latch = new CountDownLatch(jobCount);

        List<String> createdIds = new ArrayList<>();
        for (int i = 0; i < jobCount; i++) {
            Job job = new Job("Concurrent Job " + i, JobType.SIMULATED_IO, 0, "{\"durationMs\": 25}", 3);
            jobRepository.save(job);
            createdIds.add(job.getId());
        }

        // Concurrently submit into the execution queue
        for (String id : createdIds) {
            submitterPool.submit(() -> {
                try {
                    executionEngine.enqueue(id);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "All jobs should be enqueued within 5s");
        submitterPool.shutdown();

        // Wait for workers to process all jobs (polling database until terminal)
        long deadline = System.currentTimeMillis() + 10000;
        boolean allFinished = false;
        while (System.currentTimeMillis() < deadline) {
            long remaining = jobRepository.countByStatus(JobStatus.QUEUED)
                    + jobRepository.countByStatus(JobStatus.RUNNING);
            if (remaining == 0) {
                allFinished = true;
                break;
            }
            Thread.sleep(100);
        }

        assertTrue(allFinished, "All jobs should finish processing within 10s");

        List<Job> finishedJobs = jobRepository.findAll();
        assertEquals(jobCount, finishedJobs.size());

        Set<String> activeWorkers = new HashSet<>();
        for (Job job : finishedJobs) {
            assertEquals(JobStatus.SUCCEEDED, job.getStatus());
            assertEquals(1, job.getAttemptCount());
            assertNotNull(job.getWorkerId());
            activeWorkers.add(job.getWorkerId());
        }

        // Verify that multiple workers actually participated concurrently
        assertTrue(activeWorkers.size() > 1,
                "Expected multiple worker threads to participate, but got: " + activeWorkers);
    }
}
