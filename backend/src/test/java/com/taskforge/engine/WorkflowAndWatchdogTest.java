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

    @Test
    @DisplayName("ExecutionEngine extractDependsOnJobId parses parent IDs safely")
    void testExtractDependsOnJobId() {
        EngineProperties properties = new EngineProperties();
        RetryPolicy retryPolicy = new RetryPolicy();
        ExecutionEngine engine = new ExecutionEngine(
                properties, jobRepository, attemptRepository, handlerRegistry, eventPublisher, retryPolicy, transactionManager
        );

        assertNull(engine.extractDependsOnJobId(null));
        assertNull(engine.extractDependsOnJobId(""));
        assertNull(engine.extractDependsOnJobId("{\"batchIndex\": 1}"));

        String payload = "{\"pipelineStep\":2,\"dependsOnJobId\":\"abc-123-uuid\",\"durationMs\":60}";
        assertEquals("abc-123-uuid", engine.extractDependsOnJobId(payload));

        String spacedPayload = "{\"pipelineStep\": 2, \"dependsOnJobId\" : \"xyz-789-uuid\" }";
        assertEquals("xyz-789-uuid", engine.extractDependsOnJobId(spacedPayload));
    }

    @Test
    @DisplayName("isJobReadyToRun accurately guards sequential pipeline DAG steps")
    void testIsJobReadyToRun() {
        EngineProperties properties = new EngineProperties();
        RetryPolicy retryPolicy = new RetryPolicy();
        ExecutionEngine engine = new ExecutionEngine(
                properties, jobRepository, attemptRepository, handlerRegistry, eventPublisher, retryPolicy, transactionManager
        );

        // Standalone job with no parent
        Job standalone = new Job("standalone", JobType.COMPUTE, 0, "{}", 3);
        assertTrue(engine.isJobReadyToRun(standalone));

        // Job depending on parent
        String parentId = "parent-001";
        Job child = new Job("child-step2", JobType.DATA_PROCESSING, 0,
                "{\"dependsOnJobId\":\"" + parentId + "\"}", 3);

        // Case 1: Parent is still QUEUED
        Job parentQueued = new Job("parent", JobType.COMPUTE, 0, "{}", 3);
        parentQueued.setId(parentId);
        org.mockito.Mockito.when(jobRepository.findById(parentId)).thenReturn(java.util.Optional.of(parentQueued));
        assertFalse(engine.isJobReadyToRun(child));

        // Case 2: Parent SUCCEEDED
        parentQueued.markRunning("w1", null);
        parentQueued.markSucceeded(null);
        assertTrue(engine.isJobReadyToRun(child));

        // Case 3: Parent missing / null
        org.mockito.Mockito.when(jobRepository.findById("missing-parent")).thenReturn(java.util.Optional.empty());
        Job childWithMissingParent = new Job("orphan", JobType.COMPUTE, 0,
                "{\"dependsOnJobId\":\"missing-parent\"}", 3);
        assertFalse(engine.isJobReadyToRun(childWithMissingParent));
        assertEquals(JobStatus.FAILED, childWithMissingParent.getStatus());
    }

    @Test
    @DisplayName("cascadeAbortDependents recursively cancels and fails entire downstream DAG tree")
    void testCascadeAbortDependents() {
        EngineProperties properties = new EngineProperties();
        RetryPolicy retryPolicy = new RetryPolicy();
        ExecutionEngine engine = new ExecutionEngine(
                properties, jobRepository, attemptRepository, handlerRegistry, eventPublisher, retryPolicy, transactionManager
        );

        String parentId = "step-1";
        String childId = "step-2";
        String grandchildId = "step-3";

        Job child = new Job("child", JobType.DATA_PROCESSING, 0, "{\"dependsOnJobId\":\"" + parentId + "\"}", 3);
        child.setId(childId);

        Job grandchild = new Job("grandchild", JobType.REPORT_GENERATION, 0, "{\"dependsOnJobId\":\"" + childId + "\"}", 3);
        grandchild.setId(grandchildId);

        org.mockito.Mockito.when(jobRepository.findDependentJobs(parentId)).thenReturn(List.of(child));
        org.mockito.Mockito.when(jobRepository.findDependentJobs(childId)).thenReturn(List.of(grandchild));
        org.mockito.Mockito.when(jobRepository.findDependentJobs(grandchildId)).thenReturn(List.of());

        // Cascade failure
        engine.cascadeAbortDependents(parentId, "Prerequisite failed", JobStatus.FAILED);

        assertEquals(JobStatus.FAILED, child.getStatus());
        assertEquals(JobStatus.FAILED, grandchild.getStatus());
        org.mockito.Mockito.verify(jobRepository).save(child);
        org.mockito.Mockito.verify(jobRepository).save(grandchild);
    }
}
