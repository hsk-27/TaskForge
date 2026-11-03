package com.taskforge.engine;

import com.taskforge.event.JobEvent;
import com.taskforge.event.JobEventJournal;
import com.taskforge.event.JobEventListener;
import com.taskforge.event.JobEventType;
import com.taskforge.model.Job;
import com.taskforge.model.JobResult;
import com.taskforge.model.JobStatus;
import com.taskforge.queue.BoundedJobQueue;
import com.taskforge.queue.QueueFullException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The top-level orchestrator for TaskForge Milestone 1.
 *
 * Coordinates:
 * - In-memory job registry and lifecycle tracking
 * - Bounded FIFO job buffer with explicit backpressure
 * - Fixed worker thread pool
 * - Real-time event journaling and notification
 */
public class TaskForgeEngine implements AutoCloseable {
    private final TaskForgeConfig config;
    private final BoundedJobQueue queue;
    private final JobEventJournal journal;
    private final WorkerPool workerPool;
    private final Map<String, Job> registry = new ConcurrentHashMap<>();
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    public TaskForgeEngine(TaskForgeConfig config) {
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        this.queue = new BoundedJobQueue(config.queueCapacity());
        this.journal = new JobEventJournal();
        this.workerPool = new WorkerPool(config.workerCount(), this.queue, this.journal);
    }

    public TaskForgeEngine(int workerCount, int queueCapacity) {
        this(TaskForgeConfig.of(workerCount, queueCapacity));
    }

    public static TaskForgeEngine createDefault() {
        return new TaskForgeEngine(TaskForgeConfig.defaultConfig());
    }

    /**
     * Starts the worker pool and readies the engine for processing.
     */
    public synchronized TaskForgeEngine start() {
        workerPool.start();
        return this;
    }

    /**
     * Submits a job with non-blocking queue insertion.
     * If the queue is at capacity, throws {@link QueueFullException} and marks the job as FAILED.
     *
     * @param job the job to submit
     * @throws QueueFullException if the bounded queue is full
     * @throws IllegalStateException if the engine is already shutdown
     */
    public void submit(Job job) {
        Objects.requireNonNull(job, "Job cannot be null");
        ensureActive();

        registry.put(job.getId(), job);
        journal.record(JobEvent.of(job.getId(), JobEventType.SUBMITTED, "Job submitted: " + job.getName()));

        boolean enqueued = queue.offer(job);
        if (enqueued) {
            job.markQueued(Instant.now());
            journal.record(JobEvent.of(job.getId(), JobEventType.ENQUEUED,
                    "Enqueued in buffer (" + queue.size() + "/" + queue.capacity() + ")"));
        } else {
            job.markFailed(JobResult.failure("Rejected: queue capacity (" + config.queueCapacity() + ") exceeded"), Instant.now());
            journal.record(JobEvent.of(job.getId(), JobEventType.REJECTED,
                    "Submission rejected: queue is full (" + queue.size() + "/" + queue.capacity() + ")"));
            throw new QueueFullException(
                    "Cannot enqueue job " + job.getId() + ": queue capacity of " + config.queueCapacity() + " reached",
                    config.queueCapacity());
        }
    }

    /**
     * Non-blocking submission attempt that returns false instead of throwing when full.
     */
    public boolean trySubmit(Job job) {
        try {
            submit(job);
            return true;
        } catch (QueueFullException e) {
            return false;
        }
    }

    /**
     * Submits a job, blocking the calling thread if the bounded queue is currently full.
     *
     * @param job the job to submit
     * @throws InterruptedException if interrupted while waiting for space in the queue
     * @throws IllegalStateException if the engine is already shutdown
     */
    public void submitBlocking(Job job) throws InterruptedException {
        Objects.requireNonNull(job, "Job cannot be null");
        ensureActive();

        registry.put(job.getId(), job);
        journal.record(JobEvent.of(job.getId(), JobEventType.SUBMITTED, "Job submitted: " + job.getName()));

        queue.put(job);
        job.markQueued(Instant.now());
        journal.record(JobEvent.of(job.getId(), JobEventType.ENQUEUED,
                "Enqueued in buffer (" + queue.size() + "/" + queue.capacity() + ")"));
    }

    /**
     * Registers an event listener to observe execution milestones in real-time.
     */
    public void addEventListener(JobEventListener listener) {
        journal.addListener(listener);
    }

    /**
     * Retrieves a job by ID.
     */
    public Job getJob(String id) {
        return registry.get(id);
    }

    /**
     * Returns an unmodifiable snapshot list of all submitted jobs.
     */
    public List<Job> getAllJobs() {
        return Collections.unmodifiableList(new ArrayList<>(registry.values()));
    }

    public BoundedJobQueue getQueue() {
        return queue;
    }

    public JobEventJournal getJournal() {
        return journal;
    }

    public TaskForgeConfig getConfig() {
        return config;
    }

    public boolean isShutdown() {
        return isShutdown.get();
    }

    /**
     * Initiates graceful shutdown: stop accepting new submissions and drain queue.
     */
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            workerPool.shutdown();
        }
    }

    /**
     * Initiates immediate shutdown: cancels pending jobs and interrupts active workers.
     */
    public void shutdownNow() {
        isShutdown.set(true);
        workerPool.shutdownNow();
    }

    /**
     * Waits for all workers to finish.
     */
    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        return workerPool.awaitTermination(timeout);
    }

    @Override
    public void close() {
        shutdown();
        try {
            awaitTermination(Duration.ofSeconds(10));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdownNow();
        }
    }

    private void ensureActive() {
        if (isShutdown.get()) {
            throw new IllegalStateException("TaskForgeEngine has been shut down; rejecting new submissions");
        }
    }
}
