package com.taskforge.test;

import com.taskforge.engine.TaskForgeEngine;
import com.taskforge.model.Job;
import com.taskforge.model.JobStatus;
import com.taskforge.model.SimulatedJob;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.taskforge.test.Assert.*;

/**
 * Validates concurrent execution across multiple worker threads.
 */
public class ConcurrentExecutionTest {

    public void testConcurrentExecutionAcrossWorkerPool() throws Exception {
        int workerCount = 4;
        int jobCount = 32;
        int queueCapacity = 50;

        try (TaskForgeEngine engine = new TaskForgeEngine(workerCount, queueCapacity).start()) {
            for (int i = 0; i < jobCount; i++) {
                String id = "concurrent-job-" + i;
                // Mix fast jobs (10ms) with zero-delay jobs
                Duration delay = (i % 2 == 0) ? Duration.ofMillis(10) : Duration.ZERO;
                Job job = SimulatedJob.success(id, "Job #" + i, delay, "Processed item " + i);
                engine.submit(job);
            }

            // Initiate graceful shutdown and wait for all jobs to complete
            engine.shutdown();
            boolean terminated = engine.awaitTermination(Duration.ofSeconds(10));
            assertTrue(terminated, "Engine worker pool must terminate within 10 seconds");

            List<Job> allJobs = engine.getAllJobs();
            assertEquals(jobCount, allJobs.size(), "All " + jobCount + " jobs must be in the registry");

            Set<String> participatingWorkers = new HashSet<>();
            for (Job job : allJobs) {
                assertEquals(JobStatus.COMPLETED, job.getStatus(), "Job " + job.getId() + " must be COMPLETED");
                assertEquals(1, job.getAttemptCount(), "Job " + job.getId() + " must have attemptCount == 1");
                assertNotNull(job.getAssignedWorker(), "Job " + job.getId() + " must have an assigned worker");
                participatingWorkers.add(job.getAssignedWorker());
            }

            // Ensure concurrency: more than 1 worker must have participated
            assertTrue(participatingWorkers.size() > 1,
                    "Expected multiple workers to participate in execution, but only " + participatingWorkers.size() + " did: " + participatingWorkers);
        }
    }
}
