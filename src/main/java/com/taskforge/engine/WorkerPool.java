package com.taskforge.engine;

import com.taskforge.event.JobEvent;
import com.taskforge.event.JobEventJournal;
import com.taskforge.event.JobEventType;
import com.taskforge.model.Job;
import com.taskforge.model.JobExecutionContext;
import com.taskforge.model.JobResult;
import com.taskforge.model.JobStatus;
import com.taskforge.queue.BoundedJobQueue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages a fixed pool of dedicated worker threads for processing jobs from a {@link BoundedJobQueue}.
 *
 * Concurrency Primitives Used:
 * - {@link Thread} with descriptive names for deterministic traceability.
 * - {@link AtomicBoolean} for lock-free worker lifecycle coordination (start / shutdown).
 * - Thread interruption and {@link Thread#join(long)} for deterministic shutdown and termination awaiting.
 */
public class WorkerPool {
    private final int workerCount;
    private final BoundedJobQueue queue;
    private final JobEventJournal journal;
    private final List<Thread> workers;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);

    public WorkerPool(int workerCount, BoundedJobQueue queue, JobEventJournal journal) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("Worker count must be > 0, got " + workerCount);
        }
        this.workerCount = workerCount;
        this.queue = Objects.requireNonNull(queue, "Queue cannot be null");
        this.journal = Objects.requireNonNull(journal, "Journal cannot be null");
        this.workers = new ArrayList<>(workerCount);
    }

    /**
     * Initializes and starts all worker threads in the pool.
     */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            for (int i = 0; i < workerCount; i++) {
                String threadName = "taskforge-worker-" + i;
                WorkerRunnable runnable = new WorkerRunnable(threadName);
                Thread thread = new Thread(runnable, threadName);
                thread.setDaemon(false);
                workers.add(thread);
                thread.start();
            }
        }
    }

    /**
     * Initiates a graceful shutdown:
     * No new jobs will be processed once the current queue is drained.
     */
    public void shutdown() {
        if (shutdownInitiated.compareAndSet(false, true)) {
            running.set(false);
        }
    }

    /**
     * Initiates immediate shutdown:
     * Attempts to interrupt all actively executing worker threads.
     */
    public void shutdownNow() {
        shutdownInitiated.set(true);
        running.set(false);
        for (Thread worker : workers) {
            worker.interrupt();
        }
    }

    /**
     * Blocks until all worker threads have completed execution, or the timeout occurs.
     *
     * @param timeout maximum time to wait
     * @return true if all workers terminated cleanly, false if timeout elapsed
     * @throws InterruptedException if current thread was interrupted while waiting
     */
    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        long remainingMillis = timeout.toMillis();
        long deadline = System.currentTimeMillis() + remainingMillis;

        for (Thread worker : workers) {
            long waitTime = deadline - System.currentTimeMillis();
            if (waitTime <= 0) {
                return !worker.isAlive();
            }
            worker.join(waitTime);
            if (worker.isAlive()) {
                return false;
            }
        }
        return true;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isShutdown() {
        return shutdownInitiated.get();
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public List<Thread> getWorkers() {
        return Collections.unmodifiableList(workers);
    }

    /**
     * The runnable task executed by each worker thread in the pool.
     */
    private class WorkerRunnable implements Runnable {
        private final String threadName;

        WorkerRunnable(String threadName) {
            this.threadName = threadName;
        }

        @Override
        public void run() {
            // Workers run while engine is active, OR until the queue is completely drained during graceful shutdown
            while (running.get() || !queue.isEmpty()) {
                Job job = null;
                try {
                    // Poll with a short timeout to periodically check the running flag
                    job = queue.poll(Duration.ofMillis(100));
                } catch (InterruptedException e) {
                    // Interrupted during shutdownNow() or while waiting
                    if (!running.get() && queue.isEmpty()) {
                        break;
                    }
                }

                if (job != null) {
                    processJob(job);
                }
            }
        }

        private void processJob(Job job) {
            journal.record(JobEvent.of(job.getId(), JobEventType.DEQUEUED, threadName, "Dequeued by " + threadName));
            Instant start = Instant.now();
            try {
                job.markStarted(threadName, start);
                journal.record(JobEvent.of(job.getId(), JobEventType.STARTED, threadName,
                        "Attempt #" + job.getAttemptCount() + " started"));

                JobExecutionContext context = new JobExecutionContext(
                        job.getId(),
                        job.getAttemptCount(),
                        threadName,
                        start
                );

                JobResult result = job.getAction().execute(context);
                if (result == null) {
                    result = JobResult.success("Executed without explicit result");
                }

                Instant finish = Instant.now();
                if (result.isSuccess()) {
                    job.markCompleted(result, finish);
                    journal.record(JobEvent.of(job.getId(), JobEventType.COMPLETED, threadName,
                            "Completed in " + job.getExecutionDuration().toMillis() + "ms: " + result.getMessage()));
                } else {
                    job.markFailed(result, finish);
                    journal.record(JobEvent.of(job.getId(), JobEventType.FAILED, threadName,
                            "Failed in " + job.getExecutionDuration().toMillis() + "ms: " + result.getMessage()));
                }
            } catch (Throwable t) {
                Instant finish = Instant.now();
                JobResult errorResult = JobResult.failure("Uncaught exception (" + t.getClass().getSimpleName() + "): " + t.getMessage(), t);
                if (job.getStatus() == JobStatus.RUNNING) {
                    job.markFailed(errorResult, finish);
                }
                journal.record(JobEvent.of(job.getId(), JobEventType.FAILED, threadName,
                        "Failed with exception: " + t.getClass().getSimpleName() + " (" + t.getMessage() + ")"));
            }
        }
    }
}
