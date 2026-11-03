package com.taskforge.test;

import com.taskforge.model.Job;
import com.taskforge.model.JobResult;
import com.taskforge.model.JobStatus;

import java.time.Instant;

import static com.taskforge.test.Assert.*;

/**
 * Validates the Job lifecycle state machine, timestamp recording, and attempt counting.
 */
public class JobStatusTransitionTest {

    public void testSuccessfulLifecycleTransitions() throws InterruptedException {
        Job job = Job.of("job-status-1", "Test Job", ctx -> JobResult.success("Success"));

        // 1. Initial state
        assertEquals(JobStatus.SUBMITTED, job.getStatus(), "Initial status must be SUBMITTED");
        assertEquals(0, job.getAttemptCount(), "Initial attempt count must be 0");
        assertNotNull(job.getCreatedAt(), "CreatedAt must be initialized");
        assertNull(job.getEnqueuedAt(), "EnqueuedAt must be null initially");
        assertNull(job.getStartedAt(), "StartedAt must be null initially");
        assertNull(job.getCompletedAt(), "CompletedAt must be null initially");

        Thread.sleep(5);
        // 2. Transition to QUEUED
        job.markQueued(Instant.now());
        assertEquals(JobStatus.QUEUED, job.getStatus(), "Status should be QUEUED");
        assertNotNull(job.getEnqueuedAt(), "EnqueuedAt should be populated");
        assertTrue(!job.getEnqueuedAt().isBefore(job.getCreatedAt()), "EnqueuedAt >= CreatedAt");

        Thread.sleep(5);
        // 3. Transition to RUNNING
        job.markStarted("worker-0", Instant.now());
        assertEquals(JobStatus.RUNNING, job.getStatus(), "Status should be RUNNING");
        assertEquals(1, job.getAttemptCount(), "Attempt count should be 1");
        assertEquals("worker-0", job.getAssignedWorker(), "Worker should be assigned");
        assertNotNull(job.getStartedAt(), "StartedAt should be populated");
        assertTrue(!job.getStartedAt().isBefore(job.getEnqueuedAt()), "StartedAt >= EnqueuedAt");

        Thread.sleep(5);
        // 4. Transition to COMPLETED
        JobResult successResult = JobResult.success("Execution completed OK");
        job.markCompleted(successResult, Instant.now());
        assertEquals(JobStatus.COMPLETED, job.getStatus(), "Status should be COMPLETED");
        assertTrue(job.getStatus().isTerminal(), "COMPLETED is a terminal state");
        assertNotNull(job.getCompletedAt(), "CompletedAt should be populated");
        assertTrue(!job.getCompletedAt().isBefore(job.getStartedAt()), "CompletedAt >= StartedAt");
        assertEquals(successResult, job.getResult(), "Result should match");
    }

    public void testFailedLifecycleTransitions() {
        Job job = Job.of("job-status-2", "Failing Job", ctx -> JobResult.failure("Fail"));

        job.markQueued(Instant.now());
        job.markStarted("worker-1", Instant.now());
        job.markFailed(JobResult.failure("DB error"), Instant.now());

        assertEquals(JobStatus.FAILED, job.getStatus(), "Status should be FAILED");
        assertTrue(job.getStatus().isTerminal(), "FAILED is a terminal state");
        assertFalse(job.getResult().isSuccess(), "Result must indicate failure");
        assertEquals("DB error", job.getResult().getMessage(), "Error message should match");
    }

    public void testInvalidTransitionsThrowExceptions() {
        // Cannot jump directly from SUBMITTED to RUNNING
        Job j1 = Job.of("j1", "J1", ctx -> JobResult.success(""));
        assertThrows(IllegalStateException.class, () -> j1.markStarted("w1", Instant.now()));

        // Cannot jump directly from SUBMITTED to COMPLETED
        Job j2 = Job.of("j2", "J2", ctx -> JobResult.success(""));
        assertThrows(IllegalStateException.class, () -> j2.markCompleted(JobResult.success(""), Instant.now()));

        // Cannot transition out of COMPLETED terminal state
        Job j3 = Job.of("j3", "J3", ctx -> JobResult.success(""));
        j3.markQueued(Instant.now());
        j3.markStarted("w1", Instant.now());
        j3.markCompleted(JobResult.success(""), Instant.now());

        assertThrows(IllegalStateException.class, () -> j3.markStarted("w2", Instant.now()));
        assertThrows(IllegalStateException.class, () -> j3.markFailed(JobResult.failure("err"), Instant.now()));
    }
}
