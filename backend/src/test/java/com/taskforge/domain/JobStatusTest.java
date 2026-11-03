package com.taskforge.domain;

import com.taskforge.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobStatusTest {

    @Test
    void testTerminalStatesCannotTransition() {
        assertTrue(JobStatus.SUCCEEDED.isTerminal());
        assertTrue(JobStatus.FAILED.isTerminal());
        assertTrue(JobStatus.CANCELLED.isTerminal());
        assertFalse(JobStatus.QUEUED.isTerminal());
        assertFalse(JobStatus.RUNNING.isTerminal());
        assertFalse(JobStatus.RETRY_WAIT.isTerminal());

        assertFalse(JobStatus.SUCCEEDED.canTransitionTo(JobStatus.RUNNING));
        assertFalse(JobStatus.FAILED.canTransitionTo(JobStatus.RUNNING));
        assertFalse(JobStatus.CANCELLED.canTransitionTo(JobStatus.QUEUED));
    }

    @Test
    void testValidLifecycleTransitions() {
        assertTrue(JobStatus.QUEUED.canTransitionTo(JobStatus.RUNNING));
        assertTrue(JobStatus.QUEUED.canTransitionTo(JobStatus.CANCELLED));
        assertTrue(JobStatus.QUEUED.canTransitionTo(JobStatus.FAILED));

        assertTrue(JobStatus.RUNNING.canTransitionTo(JobStatus.SUCCEEDED));
        assertTrue(JobStatus.RUNNING.canTransitionTo(JobStatus.FAILED));
        assertTrue(JobStatus.RUNNING.canTransitionTo(JobStatus.RETRY_WAIT));
        assertTrue(JobStatus.RUNNING.canTransitionTo(JobStatus.CANCELLED));

        assertTrue(JobStatus.RETRY_WAIT.canTransitionTo(JobStatus.QUEUED));
        assertTrue(JobStatus.RETRY_WAIT.canTransitionTo(JobStatus.CANCELLED));
        assertTrue(JobStatus.RETRY_WAIT.canTransitionTo(JobStatus.FAILED));
    }

    @Test
    void testInvalidTransitions() {
        assertFalse(JobStatus.QUEUED.canTransitionTo(JobStatus.SUCCEEDED));
        assertFalse(JobStatus.QUEUED.canTransitionTo(JobStatus.RETRY_WAIT));
        assertFalse(JobStatus.RETRY_WAIT.canTransitionTo(JobStatus.RUNNING));
    }

    @Test
    void testInvalidStateTransitionExceptionOnJobEntity() {
        Job job = new Job("test-job", JobType.COMPUTE, 0, "{}", 3);
        assertEquals(JobStatus.QUEUED, job.getStatus());

        // Attempt invalid jump from QUEUED directly to SUCCEEDED
        assertThrows(InvalidStateTransitionException.class, () -> job.transitionTo(JobStatus.SUCCEEDED));

        // Mark terminal
        job.markRunning("worker-1", null);
        job.markSucceeded(null);
        assertEquals(JobStatus.SUCCEEDED, job.getStatus());

        // Succeeded is terminal, cannot transition back
        assertThrows(InvalidStateTransitionException.class, () -> job.transitionTo(JobStatus.RUNNING));
    }
}
