package com.taskforge.engine;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CS Theory: Bounded Buffer & Producer-Consumer Pattern
 *
 * This class implements a thread-safe, bounded, priority-aware execution buffer.
 * It serves as the shared channel between HTTP producers (REST requests submitting tasks)
 * and background consumers (worker threads executing jobs).
 *
 * Key Engineering Decisions:
 * 1. Priority Ordering: Uses java.util.concurrent.PriorityBlockingQueue so jobs with higher
 *    priority integer are pulled first. Equal-priority jobs use a sequence counter for strict FIFO.
 * 2. Backpressure / Bounded Capacity: Standard PriorityBlockingQueue in Java is unbounded by default.
 *    We enforce an explicit upper bound using an AtomicInteger with a lock-free CAS (Compare-And-Swap)
 *    loop. If capacity is exhausted, offer() returns false, signaling HTTP 429 Too Many Requests.
 * 3. O(1) Deduplication: A ConcurrentHashMap-backed Set ensures that the same jobId cannot be queued
 *    twice simultaneously, preventing redundant duplicate executions.
 */
public class JobQueue {
    // Underlying thread-safe heap for priority ordering
    private final PriorityBlockingQueue<QueuedJobItem> queue;

    // Maximum number of items permitted in memory buffer at one time
    private final int capacity;

    // Atomic tracker for bounded queue size (enables lock-free CAS capacity checks)
    private final AtomicInteger size = new AtomicInteger(0);

    // Global monotonic counter to break priority ties in strict FIFO order
    private final AtomicLong seqCounter = new AtomicLong(0);

    // Set of active job IDs currently sitting in the queue (for O(1) deduplication)
    private final Set<String> enqueuedJobIds = ConcurrentHashMap.newKeySet();

    public JobQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be > 0, got " + capacity);
        }
        this.capacity = capacity;
        this.queue = new PriorityBlockingQueue<>(capacity);
    }

    /**
     * Non-blocking attempt to enqueue a job ID with default priority (0).
     */
    public boolean offer(String jobId) {
        return offer(jobId, 0);
    }

    /**
     * Non-blocking attempt to enqueue a job with explicit priority.
     * Uses a lock-free CAS loop to verify capacity before inserting.
     *
     * @param jobId unique job identifier
     * @param priority integer priority (higher value = earlier execution)
     * @return true if accepted, false if capacity limit reached (backpressure)
     */
    public boolean offer(String jobId, int priority) {
        Objects.requireNonNull(jobId, "jobId cannot be null");

        // Step 1: O(1) deduplication check
        if (!enqueuedJobIds.add(jobId)) {
            // Already queued, avoid duplicate submission
            return true;
        }

        // Step 2: Optimistic Compare-And-Swap (CAS) to enforce capacity bound without heavy locks
        while (true) {
            int currentSize = size.get();
            if (currentSize >= capacity) {
                // Buffer is full; rollback deduplication and signal backpressure
                enqueuedJobIds.remove(jobId);
                return false;
            }
            if (size.compareAndSet(currentSize, currentSize + 1)) {
                break; // Successfully claimed a buffer slot
            }
        }

        // Step 3: Insert into the priority heap with monotonically increasing sequence number
        QueuedJobItem item = new QueuedJobItem(jobId, priority, seqCounter.incrementAndGet());
        queue.offer(item);
        return true;
    }

    /**
     * Blocking poll with timeout: Worker threads call this to wait for work.
     */
    public String poll(Duration timeout) throws InterruptedException {
        QueuedJobItem item = queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (item != null) {
            size.decrementAndGet();
            enqueuedJobIds.remove(item.getJobId());
            return item.getJobId();
        }
        return null;
    }

    /**
     * Non-blocking poll: Immediately retrieves next highest priority job, or null if empty.
     */
    public String poll() {
        QueuedJobItem item = pollItem();
        return item != null ? item.getJobId() : null;
    }

    public QueuedJobItem pollItem() {
        QueuedJobItem item = queue.poll();
        if (item != null) {
            size.decrementAndGet();
            enqueuedJobIds.remove(item.getJobId());
            return item;
        }
        return null;
    }

    public String peek() {
        QueuedJobItem item = queue.peek();
        return item != null ? item.getJobId() : null;
    }

    /**
     * Removes a job from the queue if present (e.g. when an operator cancels it before execution).
     */
    public boolean remove(String jobId) {
        if (jobId == null) return false;
        boolean removed = queue.removeIf(item -> item.getJobId().equals(jobId));
        if (removed) {
            size.decrementAndGet();
            enqueuedJobIds.remove(jobId);
        }
        return removed;
    }

    public boolean contains(String jobId) {
        if (jobId == null) return false;
        return enqueuedJobIds.contains(jobId);
    }

    public int size() {
        return size.get();
    }

    public int getCapacity() {
        return capacity;
    }

    public int remainingCapacity() {
        return Math.max(0, capacity - size.get());
    }

    public boolean isEmpty() {
        return size.get() == 0;
    }

    public boolean isFull() {
        return size.get() >= capacity;
    }

    public void clear() {
        queue.clear();
        enqueuedJobIds.clear();
        size.set(0);
    }
}
