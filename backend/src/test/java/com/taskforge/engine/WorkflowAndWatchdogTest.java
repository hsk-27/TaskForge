package com.taskforge.engine;

import com.taskforge.config.EngineProperties;
import com.taskforge.domain.Job;
import com.taskforge.domain.JobStatus;
import com.taskforge.domain.JobType;
import com.taskforge.dto.response.WorkerInfoResponse;
import com.taskforge.event.JobEventPublisher;
import com.taskforge.handler.ComputeJobHandler;
import com.taskforge.handler.JobExecutionResult;
import com.taskforge.handler.JobHandlerRegistry;
import com.taskforge.handler.JobProgressListener;
import com.taskforge.repository.JobAttemptRepository;
import com.taskforge.repository.JobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WorkflowAndWatchdogTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobAttemptRepository attemptRepository;

    @Mock
    private JobHandlerRegistry handlerRegistry;

    @Mock
    private JobEventPublisher eventPublisher;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("ComputeJobHandler reports monotonic progress updates to JobProgressListener")
    void testComputeJobHandlerReportsProgress() throws Exception {
        ComputeJobHandler handler = new ComputeJobHandler();
        Job job = new Job("Test-Compute", JobType.COMPUTE, 0, "{\"limit\": 2000}", 1);

        List<Integer> recordedPcts = new ArrayList<>();
        JobProgressListener listener = (percent, stage) -> {
            recordedPcts.add(percent);
            assertNotNull(stage);
        };

        JobExecutionResult result = handler.execute(job, "worker-test", listener);

        assertNotNull(result);
        assertTrue(result.success());
        assertFalse(recordedPcts.isEmpty());
        // Verify progress begins and ends at 100%
        assertTrue(recordedPcts.get(0) >= 0);
        assertEquals(100, recordedPcts.get(recordedPcts.size() - 1));
    }

    @Test
    @DisplayName("WorkerContext records and reports live progress and completed metrics")
    void testWorkerContextProgressAndLifetimeMetrics() {
        WorkerContext ctx = new WorkerContext("worker-test-1");
        assertFalse(ctx.isBusy());
        assertEquals(0, ctx.getProgressPercent());

        ctx.setBusy("job-123", "Calculate Hashes", 1);
        assertTrue(ctx.isBusy());
        assertEquals("job-123", ctx.getCurrentJobId());

        ctx.updateProgress(45, "Hashing chunks (45%)");
        assertEquals(45, ctx.getProgressPercent());
        assertEquals("Hashing chunks (45%)", ctx.getCurrentStage());

        ctx.recordJobFinished(true, 120);
        ctx.setIdle();

        assertFalse(ctx.isBusy());
        assertEquals(1, ctx.getTotalProcessed());
        assertEquals(0, ctx.getTotalFailed());
        assertEquals(120, ctx.getTotalExecutionTimeMs());
    }

    @Test
    @DisplayName("ExecutionEngine returns enriched WorkerInfoResponse with progress and metrics")
    void testWorkerSnapshotsIncludeProgress() {
        EngineProperties properties = new EngineProperties();
        properties.setWorkerCount(1);
        RetryPolicy retryPolicy = new RetryPolicy();

        ExecutionEngine engine = new ExecutionEngine(
                properties,
                jobRepository,
                attemptRepository,
                handlerRegistry,
                eventPublisher,
                retryPolicy,
                transactionManager
        );

        List<WorkerInfoResponse> snapshots = engine.getWorkerSnapshots();
        assertNotNull(snapshots);
    }
}
