package com.taskforge.handler;

import com.taskforge.domain.Job;
import com.taskforge.domain.JobType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobHandlerTest {

    @Test
    void testComputeJobHandler() throws Exception {
        ComputeJobHandler handler = new ComputeJobHandler();
        Job job = new Job("Prime Calculation", JobType.COMPUTE, 0, "{\"limit\": 2000}", 3);
        JobExecutionResult result = handler.execute(job, "worker-1");

        assertTrue(result.success());
        assertNotNull(result.output());
        assertTrue(result.output().contains("primes"));
    }

    @Test
    void testDataProcessingJobHandler() throws Exception {
        DataProcessingJobHandler handler = new DataProcessingJobHandler();
        Job job = new Job("Aggregation Task", JobType.DATA_PROCESSING, 0, null, 3);
        JobExecutionResult result = handler.execute(job, "worker-1");

        assertTrue(result.success());
        assertTrue(result.output().contains("mean="));
    }

    @Test
    void testFailureDemoDeterministicFailure() throws Exception {
        FailureDemoJobHandler handler = new FailureDemoJobHandler();
        Job job = new Job("Failure Injection", JobType.FAILURE_DEMO, 0, "{\"failureType\": \"ALWAYS_FAIL\"}", 3);
        JobExecutionResult result = handler.execute(job, "worker-1");

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void testFailureDemoUncaughtException() {
        FailureDemoJobHandler handler = new FailureDemoJobHandler();
        Job job = new Job("Crash Test", JobType.FAILURE_DEMO, 0, "{\"mode\": \"THROW_UNCAUGHT\"}", 3);

        assertThrows(RuntimeException.class, () -> handler.execute(job, "worker-1"));
    }
}
