package com.taskforge.test;

import com.taskforge.engine.TaskForgeEngine;
import com.taskforge.model.Job;
import com.taskforge.model.JobStatus;
import com.taskforge.model.SimulatedJob;

import java.time.Duration;

import static com.taskforge.test.Assert.*;

/**
 * Validates graceful shutdown, queue draining, and rejection of new submissions post-shutdown.
 */
public class ShutdownAndDrainTest {

    public void testGracefulShutdownDrainsQueue() throws Exception {
        TaskForgeEngine engine = new TaskForgeEngine(2, 20).start();

        Job j1 = SimulatedJob.success("drain-1", "Draining Job 1", Duration.ofMillis(30), "OK");
        Job j2 = SimulatedJob.success("drain-2", "Draining Job 2", Duration.ofMillis(30), "OK");
        Job j3 = SimulatedJob.success("drain-3", "Draining Job 3", Duration.ofMillis(30), "OK");

        engine.submit(j1);
        engine.submit(j2);
        engine.submit(j3);

        // Initiate shutdown immediately
        engine.shutdown();
        assertTrue(engine.isShutdown(), "Engine should report isShutdown == true");

        // Submitting after shutdown must be rejected
        Job j4 = SimulatedJob.success("drain-4", "Rejected Job 4", Duration.ZERO, "OK");
        assertThrows(IllegalStateException.class, () -> engine.submit(j4));

        // Await termination: workers must finish draining j1, j2, j3
        boolean terminated = engine.awaitTermination(Duration.ofSeconds(5));
        assertTrue(terminated, "Engine should terminate within 5 seconds");

        assertEquals(JobStatus.COMPLETED, j1.getStatus(), "j1 must complete");
        assertEquals(JobStatus.COMPLETED, j2.getStatus(), "j2 must complete");
        assertEquals(JobStatus.COMPLETED, j3.getStatus(), "j3 must complete");
    }
}
