package com.taskforge.engine;

import com.taskforge.config.EngineProperties;
import com.taskforge.domain.Job;
import com.taskforge.domain.JobAttempt;
import com.taskforge.domain.JobStatus;
import com.taskforge.dto.response.WorkerInfoResponse;
import com.taskforge.event.JobEventPublisher;
import com.taskforge.exception.QueueFullException;
import com.taskforge.handler.JobExecutionResult;
import com.taskforge.handler.JobHandler;
import com.taskforge.handler.JobHandlerRegistry;
import com.taskforge.repository.JobAttemptRepository;
import com.taskforge.repository.JobRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CS Theory: Operating Systems Multi-Threading & Distributed Engine Architecture
 *
 * This class serves as the central brain of TaskForge. It orchestrates:
 * 1. Worker Thread Pool Lifecycle: Spawns dedicated OS worker threads (named taskforge-worker-0..N)
 *    that continually consume tasks from the bounded JobQueue.
 * 2. Two-Phase Transaction Decoupling:
 *    - Phase 1 (claimJob): Short DB transaction acquires lock, marks job as RUNNING, commits immediately.
 *    - Task Execution: The actual task computation runs COMPLETELY OUTSIDE database transactions.
 *      This ensures database connections from HikariCP are not held hostage during long-running tasks,
 *      preventing connection pool starvation!
 *    - Phase 2 (recordSuccess/recordFailure): Short DB transaction records terminal state and creates
 *      the attempt audit record.
 * 3. Crash Recovery (Fault Tolerance):
 *    On JVM reboot, detects jobs stranded in RUNNING state from sudden power failures or crashes
 *    and reschedules them safely.
 * 4. Stalled Worker Watchdog (SLA Guard):
 *    Periodically inspects worker threads to interrupt tasks that exceed execution timeouts.
 */
@Service
@EnableScheduling
public class ExecutionEngine {
    private static final Logger log = LoggerFactory.getLogger(ExecutionEngine.class);

    private final EngineProperties properties;
    private final JobRepository jobRepository;
    private final JobAttemptRepository attemptRepository;
    private final JobHandlerRegistry handlerRegistry;
    private final JobEventPublisher eventPublisher;
    private final RetryPolicy retryPolicy;
    private final TransactionTemplate transactionTemplate;

    private volatile JobQueue queue;
    private final Map<String, WorkerThread> workers = new ConcurrentHashMap<>();
    private final Map<String, WorkerContext> workerContexts = new ConcurrentHashMap<>();

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean ecoMode = new AtomicBoolean(true);

    public record ClaimedJob(Job job, String attemptId) {}

    public ExecutionEngine(
            EngineProperties properties,
            JobRepository jobRepository,
            JobAttemptRepository attemptRepository,
            JobHandlerRegistry handlerRegistry,
            JobEventPublisher eventPublisher,
            RetryPolicy retryPolicy,
            PlatformTransactionManager transactionManager
    ) {
        this.properties = properties;
        this.jobRepository = jobRepository;
        this.attemptRepository = attemptRepository;
        this.handlerRegistry = handlerRegistry;
        this.eventPublisher = eventPublisher;
        this.retryPolicy = retryPolicy;
        this.ecoMode.set(properties.isEcoMode());
        this.queue = new JobQueue(properties.getQueueCapacity());
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @PostConstruct
    public synchronized void start() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("Starting TaskForge Execution Engine with {} workers and queue capacity {}",
                    properties.getWorkerCount(), properties.getQueueCapacity());
            adjustWorkerCount(properties.getWorkerCount());
            recoverInterruptedJobs();
        }
    }

    @PreDestroy
    public synchronized void shutdown() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("Shutting down TaskForge Execution Engine (grace period: {}s)...",
                    properties.getDrainTimeoutSeconds());

            // Interrupt all workers
            for (WorkerThread worker : workers.values()) {
                worker.interrupt();
            }

            long deadline = System.currentTimeMillis() + (properties.getDrainTimeoutSeconds() * 1000L);
            for (WorkerThread worker : workers.values()) {
                long waitTime = deadline - System.currentTimeMillis();
                if (waitTime > 0) {
                    try {
                        worker.join(waitTime);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            workers.clear();
            workerContexts.clear();
            log.info("Execution Engine shutdown complete.");
        }
    }

    /**
     * Submits a job ID to the execution queue with default priority (0).
     * Enforces backpressure: throws QueueFullException if capacity is reached.
     */
    public void enqueue(String jobId) {
        enqueue(jobId, 0);
    }

    /**
     * Submits a job ID with an explicit priority to the execution queue.
     * Enforces backpressure: throws QueueFullException if capacity is reached.
     */
    public void enqueue(String jobId, int priority) {
        if (!isRunning.get()) {
            throw new IllegalStateException("Execution Engine is not running");
        }
        boolean enqueued = queue.offer(jobId, priority);
        if (!enqueued) {
            throw new QueueFullException(
                    "Execution queue capacity (" + queue.getCapacity() + ") exceeded. Backpressure applied.",
                    queue.getCapacity());
        }
    }

    public synchronized void adjustWorkerCount(int targetCount) {
        targetCount = Math.max(1, Math.min(32, targetCount));
        properties.setWorkerCount(targetCount);

        int currentCount = workers.size();
        if (targetCount > currentCount) {
            for (int i = currentCount; i < targetCount; i++) {
                String workerId = "taskforge-worker-" + i;
                WorkerContext context = new WorkerContext(workerId);
                workerContexts.put(workerId, context);
                WorkerThread thread = new WorkerThread(workerId, context);
                // Lower background worker thread priority so local UI and OS remain silky smooth
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
                workers.put(workerId, thread);
                thread.start();
            }
            log.info("Scaled worker pool UP from {} to {}", currentCount, targetCount);
        } else if (targetCount < currentCount) {
            for (int i = currentCount - 1; i >= targetCount; i--) {
                String workerId = "taskforge-worker-" + i;
                WorkerThread thread = workers.remove(workerId);
                workerContexts.remove(workerId);
                if (thread != null) {
                    thread.terminate();
                    thread.interrupt();
                }
            }
            log.info("Scaled worker pool DOWN from {} to {}", currentCount, targetCount);
        }
    }

    public synchronized void updateQueueCapacity(int newCapacity) {
        newCapacity = Math.max(5, Math.min(1000, newCapacity));
        properties.setQueueCapacity(newCapacity);
        JobQueue newQueue = new JobQueue(newCapacity);
        // Transfer existing elements preserving priority
        QueuedJobItem item;
        while ((item = queue.pollItem()) != null && newQueue.remainingCapacity() > 0) {
            newQueue.offer(item.getJobId(), item.getPriority());
        }
        this.queue = newQueue;
        log.info("Updated queue capacity to {}", newCapacity);
    }

    public void setPaused(boolean pause) {
        this.isPaused.set(pause);
        log.info("Engine paused status set to: {}", pause);
    }

    public boolean isPaused() {
        return isPaused.get();
    }

    public boolean isEcoMode() {
        return ecoMode.get();
    }

    public void setEcoMode(boolean enabled) {
        this.ecoMode.set(enabled);
        this.properties.setEcoMode(enabled);
        log.info("Engine Eco-Mode set to: {}", enabled);
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public JobQueue getQueue() {
        return queue;
    }

    public List<WorkerInfoResponse> getWorkerSnapshots() {
        List<WorkerInfoResponse> list = new ArrayList<>();
        for (WorkerContext ctx : workerContexts.values()) {
            long processed = ctx.getTotalProcessed();
            double avg = processed > 0 ? (double) ctx.getTotalExecutionTimeMs() / processed : 0.0;
            list.add(new WorkerInfoResponse(
                    ctx.getWorkerId(),
                    ctx.isBusy() ? "BUSY" : "IDLE",
                    ctx.getCurrentJobId(),
                    ctx.getCurrentJobName(),
                    ctx.getAttemptNumber(),
                    ctx.getElapsedMs(),
                    ctx.getProgressPercent(),
                    ctx.getCurrentStage(),
                    processed,
                    ctx.getTotalFailed(),
                    Math.round(avg * 10.0) / 10.0
            ));
        }
        return list;
    }

    public int getActiveWorkerCount() {
        return (int) workerContexts.values().stream().filter(WorkerContext::isBusy).count();
    }

    /**
     * Interrupts the active worker thread currently executing the given job, if any.
     * Used for real-time operator cancellations.
     */
    public boolean interruptJobIfRunning(String jobId) {
        if (jobId == null) return false;
        for (Map.Entry<String, WorkerContext> entry : workerContexts.entrySet()) {
            String workerId = entry.getKey();
            WorkerContext ctx = entry.getValue();
            if (jobId.equals(ctx.getCurrentJobId())) {
                WorkerThread thread = workers.get(workerId);
                if (thread != null && thread.isAlive()) {
                    log.info("Interrupting active worker thread [{}] running cancelled job [{}]", workerId, jobId);
                    thread.interrupt();
                    return true;
                }
            }
        }
        return false;
    }

    // --- Safe Job Claiming & Execution ---

    public ClaimedJob claimJob(String jobId, String workerId) {
        return transactionTemplate.execute(status -> {
            Job job = jobRepository.findById(jobId).orElse(null);
            if (job == null) return null;

            // Ensure state is QUEUED (or RETRY_WAIT if directly dispatched)
            if (job.getStatus() != JobStatus.QUEUED && job.getStatus() != JobStatus.RETRY_WAIT) {
                log.debug("Skipping claim for job {} because status is {}", jobId, job.getStatus());
                return null;
            }

            // Sequential Pipeline / DAG Dependency Guard:
            String parentId = extractDependsOnJobId(job.getPayload());
            if (parentId != null && !parentId.isBlank()) {
                Job parent = jobRepository.findById(parentId).orElse(null);
                if (parent == null || parent.getStatus() != JobStatus.SUCCEEDED) {
                    log.debug("Job {} cannot be claimed yet: waiting on prerequisite job {}", jobId, parentId);
                    return null;
                }
            }

            job.markRunning(workerId, Instant.now());
            jobRepository.save(job);

            JobAttempt attempt = new JobAttempt(jobId, job.getAttemptCount(), workerId, job.getStartedAt());
            attemptRepository.save(attempt);

            eventPublisher.publishEvent(job, "JOB_STARTED", "Execution attempt #" + job.getAttemptCount() + " started by " + workerId);
            return new ClaimedJob(job, attempt.getId());
        });
    }

    public void recordSuccess(String jobId, String attemptId, String workerId, JobExecutionResult result) {
        transactionTemplate.executeWithoutResult(status -> {
            Job job = jobRepository.findById(jobId).orElse(null);
            JobAttempt attempt = attemptRepository.findById(attemptId).orElse(null);
            if (job == null) return;

            // Check if job was cancelled during execution
            if (job.getStatus() == JobStatus.CANCELLED) {
                if (attempt != null) {
                    attempt.complete("CANCELLED", "Execution cancelled while running", Instant.now());
                    attemptRepository.save(attempt);
                }
                return;
            }

            Instant now = Instant.now();
            job.markSucceeded(now);
            jobRepository.save(job);

            if (attempt != null) {
                attempt.complete("SUCCEEDED", result != null ? result.output() : null, now);
                attemptRepository.save(attempt);
            }

            eventPublisher.publishEvent(job, "JOB_SUCCEEDED",
                    result != null ? result.message() : "Job completed successfully");

            // Sequential Pipeline / DAG Workflow: Automatically unblock and enqueue downstream jobs
            List<Job> dependentJobs = jobRepository.findDependentJobs(jobId);
            for (Job dep : dependentJobs) {
                try {
                    boolean added = queue.offer(dep.getId(), dep.getPriority());
                    if (added) {
                        eventPublisher.publishEvent(dep, "JOB_UNBLOCKED",
                                "Prerequisite step " + jobId + " succeeded; step unblocked and enqueued");
                    }
                } catch (Exception ex) {
                    log.warn("Failed to enqueue dependent job {}: {}", dep.getId(), ex.getMessage());
                }
            }
        });
    }

    public void recordFailure(String jobId, String attemptId, String workerId, String errorMessage, Throwable error) {
        transactionTemplate.executeWithoutResult(status -> {
            Job job = jobRepository.findById(jobId).orElse(null);
            JobAttempt attempt = attemptRepository.findById(attemptId).orElse(null);
            if (job == null) return;

            if (job.getStatus() == JobStatus.CANCELLED) {
                if (attempt != null) {
                    attempt.complete("CANCELLED", "Execution cancelled while running", Instant.now());
                    attemptRepository.save(attempt);
                }
                return;
            }

            Instant now = Instant.now();
            if (attempt != null) {
                attempt.complete("FAILED", errorMessage, now);
                attemptRepository.save(attempt);
            }

            if (job.getAttemptCount() < job.getMaxAttempts()) {
                // Schedule Retry with Exponential Backoff + Jitter
                Instant nextRetryAt = retryPolicy.calculateNextRetryTime(
                        job.getAttemptCount(),
                        properties.getBaseBackoffMs(),
                        properties.getMaxBackoffMs(),
                        properties.getJitterFactor()
                );
                job.markRetryWait(errorMessage, nextRetryAt);
                jobRepository.save(job);

                long delaySec = Math.max(1, Duration.between(now, nextRetryAt).toSeconds());
                eventPublisher.publishEvent(job, "RETRY_SCHEDULED",
                        String.format("Attempt #%d failed (%s). Retry #%d scheduled in %ds",
                                job.getAttemptCount(), errorMessage, job.getAttemptCount() + 1, delaySec));
            } else {
                // Terminal Failure: attempts exhausted
                job.markFailed(errorMessage, now);
                jobRepository.save(job);

                eventPublisher.publishEvent(job, "JOB_FAILED",
                        String.format("Execution failed permanently after %d attempts: %s",
                                job.getAttemptCount(), errorMessage));

                // Fail downstream dependent jobs in the workflow pipeline
                List<Job> dependentJobs = jobRepository.findDependentJobs(jobId);
                for (Job dep : dependentJobs) {
                    dep.markFailed("Dependency failure: Prerequisite step " + jobId + " failed permanently", now);
                    jobRepository.save(dep);
                    queue.remove(dep.getId());
                    eventPublisher.publishEvent(dep, "JOB_FAILED",
                            "Pipeline aborted: prerequisite step " + jobId + " failed permanently");
                }
            }
        });
    }

    private String extractDependsOnJobId(String payload) {
        if (payload == null || !payload.contains("dependsOnJobId")) return null;
        try {
            int idx = payload.indexOf("\"dependsOnJobId\"");
            if (idx != -1) {
                int colon = payload.indexOf(':', idx);
                int quote1 = payload.indexOf('"', colon);
                int quote2 = payload.indexOf('"', quote1 + 1);
                if (quote1 != -1 && quote2 != -1) {
                    return payload.substring(quote1 + 1, quote2).trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * SLA Watchdog: Runs every 10 seconds to detect and recover stalled jobs
     * that have exceeded their maximum execution duration SLA.
     */
    @Scheduled(fixedRate = 10000)
    public void watchdogCheckStalledJobs() {
        if (!isRunning.get() || isPaused.get()) return;

        int timeoutMinutes = Math.max(1, properties.getStaleJobTimeoutMinutes());
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(timeoutMinutes));

        List<Job> stalledJobs = jobRepository.findByStatusAndStartedAtBefore(JobStatus.RUNNING, cutoff);
        for (Job job : stalledJobs) {
            log.warn("SLA Watchdog detected stalled job [{}]: running since {}", job.getId(), job.getStartedAt());
            interruptJobIfRunning(job.getId());
            recordFailure(
                    job.getId(),
                    null,
                    job.getWorkerId(),
                    "Execution exceeded SLA timeout (" + timeoutMinutes + "m). Terminated by watchdog.",
                    null
            );
            eventPublisher.publishEvent(
                    job,
                    "JOB_STALLED",
                    "Watchdog detected execution timeout (> " + timeoutMinutes + "m). Interrupted and marked for recovery."
            );
        }
    }

    // --- Periodic Dispatchers ---

    /**
     * Polls database for jobs in RETRY_WAIT whose scheduled delay has passed,
     * and moves them back to the QUEUED state.
     */
    @Scheduled(fixedDelay = 500)
    public void processScheduledRetries() {
        if (!isRunning.get() || isPaused.get()) return;

        List<Job> readyJobs = jobRepository.findByStatusAndScheduledExecutionAtBefore(
                JobStatus.RETRY_WAIT, Instant.now());

        for (Job job : readyJobs) {
            try {
                requeueRetryJob(job.getId());
            } catch (Exception ex) {
                log.error("Error requeuing retry job {}: {}", job.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Feeds pending QUEUED jobs from database into the bounded in-memory queue
     * whenever capacity becomes available.
     */
    @Scheduled(fixedDelay = 1000)
    public void drainPendingDbJobs() {
        if (!isRunning.get() || isPaused.get()) return;
        int available = queue.remainingCapacity();
        if (available <= 0) return;

        Pageable page = PageRequest.of(0, Math.min(available, 50));
        List<Job> queued = jobRepository.findByStatusOrderByPriorityDescCreatedAtAsc(JobStatus.QUEUED, page);
        for (Job job : queued) {
            if (!queue.contains(job.getId())) {
                boolean added = queue.offer(job.getId(), job.getPriority());
                if (!added) break;
            }
        }
    }

    public void requeueRetryJob(String jobId) {
        transactionTemplate.executeWithoutResult(status -> {
            Job job = jobRepository.findById(jobId).orElse(null);
            if (job == null || job.getStatus() != JobStatus.RETRY_WAIT) return;

            job.markRequeued();
            jobRepository.save(job);

            boolean enqueued = queue.offer(jobId, job.getPriority());
            if (enqueued) {
                eventPublisher.publishEvent(job, "JOB_REQUEUED",
                        "Backoff delay elapsed. Re-entered queue for attempt #" + (job.getAttemptCount() + 1));
            } else {
                // Queue full: keep in QUEUED in DB; it will be picked up by drainPendingDbJobs
                eventPublisher.publishEvent(job, "JOB_REQUEUED",
                        "Backoff delay elapsed. Waiting for queue buffer capacity.");
            }
        });
    }

    /**
     * Reconciles RUNNING jobs from a previous process crash or sudden restart.
     */
    @Transactional
    public void recoverInterruptedJobs() {
        List<Job> interruptedJobs = jobRepository.findByStatus(JobStatus.RUNNING);
        if (interruptedJobs.isEmpty()) return;

        log.warn("Found {} interrupted jobs from previous shutdown. Initiating recovery...", interruptedJobs.size());
        for (Job job : interruptedJobs) {
            String errorMsg = "Application restarted while job was in RUNNING state on worker: " + job.getWorkerId();
            // Close any dangling in-flight attempt record from previous crash
            List<JobAttempt> runningAttempts = attemptRepository.findByJobIdOrderByAttemptNumberAsc(job.getId());
            for (JobAttempt att : runningAttempts) {
                if ("RUNNING".equals(att.getOutcome())) {
                    att.complete("INTERRUPTED", errorMsg, Instant.now());
                    attemptRepository.save(att);
                }
            }
            if (job.getAttemptCount() < job.getMaxAttempts()) {
                Instant nextRetry = Instant.now().plus(Duration.ofSeconds(2));
                job.markRetryWait(errorMsg, nextRetry);
                jobRepository.save(job);
                eventPublisher.publishEvent(job, "RECOVERED_RETRY", errorMsg + ". Scheduled for recovery retry.");
            } else {
                job.markFailed(errorMsg + ". Maximum attempts exhausted.", Instant.now());
                jobRepository.save(job);
                eventPublisher.publishEvent(job, "RECOVERED_FAILED", errorMsg + ". Max attempts reached.");
            }
        }
    }

    // --- Worker Thread Implementation ---

    private class WorkerThread extends Thread {
        private final String workerId;
        private final WorkerContext context;
        private final AtomicBoolean active = new AtomicBoolean(true);

        public WorkerThread(String workerId, WorkerContext context) {
            super("taskforge-" + workerId);
            this.workerId = workerId;
            this.context = context;
        }

        public void terminate() {
            active.set(false);
        }

        @Override
        public void run() {
            while (isRunning.get() && active.get()) {
                if (isPaused.get()) {
                    try {
                        Thread.sleep(properties.getPollIntervalMs());
                    } catch (InterruptedException e) {
                        if (!active.get()) break;
                    }
                    continue;
                }

                String jobId = null;
                try {
                    jobId = queue.poll(Duration.ofMillis(properties.getPollIntervalMs()));
                } catch (InterruptedException e) {
                    if (!active.get()) break;
                }

                if (jobId != null) {
                    processJob(jobId);
                } else if (ecoMode.get()) {
                    try {
                        Thread.sleep(15);
                    } catch (InterruptedException ignored) {}
                }
            }
            context.setIdle();
        }

        /**
         * Core Worker Execution Flow (Two-Phase Transaction Pattern)
         *
         * Viva Question: "Why is task execution not inside a @Transactional method?"
         * Viva Answer: "If execution takes 5 seconds, holding a database connection for 5 seconds
         * causes HikariCP connection pool starvation. We claim the job in Transaction 1 (~2ms),
         * release the connection, run the heavy job logic outside DB locks, and save the result
         * in Transaction 2 (~2ms)."
         */
        private void processJob(String jobId) {
            ClaimedJob claim = null;
            try {
                // Step 1: Acquire job in DB (Phase 1 short transaction)
                claim = claimJob(jobId, workerId);
            } catch (Exception ex) {
                // If another worker claimed the job or state changed concurrently, skip cleanly
                log.debug("Job {} could not be claimed by {} (concurrent transition): {}", jobId, workerId, ex.getMessage());
                return;
            }

            if (claim == null) {
                return; // Job already claimed, waiting for prerequisite DAG step, or cancelled
            }

            Job job = claim.job();
            // Step 2: Mark worker thread as BUSY for live UI telemetry
            context.setBusy(job.getId(), job.getName(), job.getAttemptCount());

            long startNanos = System.nanoTime();
            boolean success = false;
            try {
                JobHandler handler = handlerRegistry.getHandler(job.getJobType());
                // Step 3: Execution takes place OUTSIDE of database transactions!
                JobExecutionResult result = handler.execute(job, workerId, (percent, stage) -> {
                    context.updateProgress(percent, stage);
                    eventPublisher.publishProgress(job.getId(), workerId, percent, stage);
                });

                // Step 4: Finalize outcome in DB (Phase 2 short transaction)
                if (result != null && result.success()) {
                    success = true;
                    recordSuccess(job.getId(), claim.attemptId(), workerId, result);
                } else {
                    String err = (result != null && result.errorMessage() != null)
                            ? result.errorMessage()
                            : "Handler reported failure";
                    recordFailure(job.getId(), claim.attemptId(), workerId, err, null);
                }
            } catch (InterruptedException ie) {
                recordFailure(job.getId(), claim.attemptId(), workerId, "Interrupted: " + ie.getMessage(), ie);
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                log.warn("Worker {} caught unhandled exception during job {}: {}", workerId, job.getId(), t.getMessage());
                recordFailure(job.getId(), claim.attemptId(), workerId, "Unhandled " + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
            } finally {
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
                context.recordJobFinished(success, durationMs);
                context.setIdle();
            }
        }
    }
}
