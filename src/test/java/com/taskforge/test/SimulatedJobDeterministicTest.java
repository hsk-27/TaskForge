package com.taskforge.test;

import com.taskforge.engine.TaskForgeEngine;
import com.taskforge.event.JobEvent;
import com.taskforge.event.JobEventType;
import com.taskforge.model.Job;
import com.taskforge.model.JobStatus;
import com.taskforge.model.SimulatedJob;

import java.time.Duration;
import java.util.List;

import static com.taskforge.test.Assert.*;

/**
 * Validates deterministic simulation of job successes, failures, and unhandled exceptions.
 */
public class SimulatedJobDeterministicTest {

    public void testDeterministicSuccess() throws Exception {
        try (TaskForgeEngine engine = new TaskForgeEngine(1, 10).start()) {
            Job successJob = SimulatedJob.success("det-success", "Successful Task", Duration.ofMillis(20), "Calculated answer: 42");
            engine.submit(successJob);

            engine.shutdown();
            assertTrue(engine.awaitTermination(Duration.ofSeconds(2)), "Engine should finish");

            assertEquals(JobStatus.COMPLETED, successJob.getStatus(), "Status should be COMPLETED");
            assertTrue(successJob.getResult().isSuccess(), "Result should be success");
            assertEquals("Calculated answer: 42", successJob.getResult().getMessage(), "Message should match");

            // Verify event stream
            List<JobEvent> events = engine.getJournal().getEventsForJob("det-success");
            assertTrue(events.stream().anyMatch(e -> e.type() == JobEventType.SUBMITTED), "Must have SUBMITTED");
            assertTrue(events.stream().anyMatch(e -> e.type() == JobEventType.ENQUEUED), "Must have ENQUEUED");
            assertTrue(events.stream().anyMatch(e -> e.type() == JobEventType.STARTED), "Must have STARTED");
            assertTrue(events.stream().anyMatch(e -> e.type() == JobEventType.COMPLETED), "Must have COMPLETED");
        }
    }

    public void testDeterministicFailure() throws Exception {
        try (TaskForgeEngine engine = new TaskForgeEngine(1, 10).start()) {
            Job failJob = SimulatedJob.failure("det-fail", "Failing Task", Duration.ofMillis(20), "Disk full simulation");
            engine.submit(failJob);

            engine.shutdown();
            assertTrue(engine.awaitTermination(Duration.ofSeconds(2)), "Engine should finish");

            assertEquals(JobStatus.FAILED, failJob.getStatus(), "Status should be FAILED");
            assertFalse(failJob.getResult().isSuccess(), "Result should indicate failure");
            assertEquals("Disk full simulation", failJob.getResult().getMessage(), "Message should match");

            List<JobEvent> events = engine.getJournal().getEventsForJob("det-fail");
            assertTrue(events.stream().anyMatch(e -> e.type() == JobEventType.FAILED), "Must have FAILED event");
        }
    }

    public void testDeterministicException() throws Exception {
        try (TaskForgeEngine engine = new TaskForgeEngine(1, 10).start()) {
            IllegalStateException thrownEx = new IllegalStateException("Corrupt internal state");
            Job exceptionalJob = SimulatedJob.exceptional("det-ex", "Crashing Task", Duration.ofMillis(20), thrownEx);
            engine.submit(exceptionalJob);

            engine.shutdown();
            assertTrue(engine.awaitTermination(Duration.ofSeconds(2)), "Engine should finish");

            assertEquals(JobStatus.FAILED, exceptionalJob.getStatus(), "Job should be marked FAILED on unhandled exception");
            assertNotNull(exceptionalJob.getResult().getError(), "Error must be recorded in result");
            assertTrue(exceptionalJob.getResult().getMessage().contains("IllegalStateException"),
                    "Message should contain exception type");
        }
    }
}
