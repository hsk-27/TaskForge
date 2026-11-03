package com.taskforge.queue;

import com.taskforge.model.Job;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread-safe Bounded FIFO Job Queue implemented from first principles
 * using Java concurrency primitives ({@link ReentrantLock} and {@link Condition}).
 *
 * Systems Engineering Concept:
 * - Backpressure: Bounded capacity prevents unbounded memory consumption
 *   and protects workers from being overwhelmed by bursty job submissions.
 * - Circular Buffer: Uses an array with head and tail pointers to achieve
 *   O(1) enqueue and dequeue operations without shifting elements.
 * - Condition Variables: Producers wait on {@code notFull} when capacity is exhausted;
 *   Consumers wait on {@code notEmpty} when the queue is drained.
 */
public class BoundedJobQueue {
    private final Job[] buffer;
    private final int capacity;

    private int head = 0;   // Index of the next element to dequeue
    private int tail = 0;   // Index where the next element will be enqueued
    private int count = 0;  // Current number of items in the queue

    private final ReentrantLock lock;
    private final Condition notEmpty;
    private final Condition notFull;

    /**
     * Constructs a bounded queue with a fixed maximum capacity.
     *
     * @param capacity maximum number of jobs the queue can hold (must be >= 1)
     */
    public BoundedJobQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be greater than 0, got " + capacity);
        }
        this.capacity = capacity;
        this.buffer = new Job[capacity];
        // Fair lock prevents thread starvation among competing workers and producers
        this.lock = new ReentrantLock(true);
        this.notEmpty = lock.newCondition();
        this.notFull = lock.newCondition();
    }

    /**
     * Inserts a job at the tail of the queue, waiting indefinitely if necessary for space to become available.
     *
     * @param job the job to enqueue
     * @throws InterruptedException if interrupted while waiting
     */
    public void put(Job job) throws InterruptedException {
        Objects.requireNonNull(job, "Cannot enqueue null job");
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            // Guard against spurious wakeups using a while loop
            while (count == capacity) {
                notFull.await();
            }
            enqueueInternal(job);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Non-blocking attempt to enqueue a job.
     *
     * @param job the job to enqueue
     * @return true if successfully enqueued, false if queue is full
     */
    public boolean offer(Job job) {
        Objects.requireNonNull(job, "Cannot enqueue null job");
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count == capacity) {
                return false;
            }
            enqueueInternal(job);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Timed attempt to enqueue a job. Waits up to the specified duration for space to become available.
     *
     * @param job the job to enqueue
     * @param timeout duration to wait
     * @return true if enqueued within timeout, false if timeout elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean offer(Job job, Duration timeout) throws InterruptedException {
        Objects.requireNonNull(job, "Cannot enqueue null job");
        long nanos = timeout.toNanos();
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == capacity) {
                if (nanos <= 0L) {
                    return false;
                }
                nanos = notFull.awaitNanos(nanos);
            }
            enqueueInternal(job);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the job at the head of the queue, waiting indefinitely if empty.
     *
     * @return the head job
     * @throws InterruptedException if interrupted while waiting
     */
    public Job take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == 0) {
                notEmpty.await();
            }
            return dequeueInternal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Timed poll: Retrieves and removes the job at the head, waiting up to the specified duration.
     *
     * @param timeout duration to wait
     * @return the head job, or null if the timeout elapsed before an element was available
     * @throws InterruptedException if interrupted while waiting
     */
    public Job poll(Duration timeout) throws InterruptedException {
        long nanos = timeout.toNanos();
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == 0) {
                if (nanos <= 0L) {
                    return null;
                }
                nanos = notEmpty.awaitNanos(nanos);
            }
            return dequeueInternal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Non-blocking poll: Retrieves and removes the head job, or returns null if empty.
     */
    public Job poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count == 0) {
                return null;
            }
            return dequeueInternal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Peeks at the head job without removing it, or returns null if empty.
     */
    public Job peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count == 0) return null;
            return buffer[head];
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    public int capacity() {
        return capacity;
    }

    public int remainingCapacity() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return capacity - count;
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return count == 0;
        } finally {
            lock.unlock();
        }
    }

    public boolean isFull() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return count == capacity;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a snapshot list of current jobs in FIFO order without removing them.
     */
    public List<Job> toList() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            List<Job> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int index = (head + i) % capacity;
                list.add(buffer[index]);
            }
            return list;
        } finally {
            lock.unlock();
        }
    }

    // --- Internal Helpers (MUST be called while holding lock) ---

    private void enqueueInternal(Job job) {
        buffer[tail] = job;
        tail = (tail + 1) % capacity;
        count++;
        // Notify one waiting consumer that a job is now available
        notEmpty.signal();
    }

    private Job dequeueInternal() {
        Job job = buffer[head];
        buffer[head] = null; // Clear reference for GC
        head = (head + 1) % capacity;
        count--;
        // Notify one waiting producer that a slot is now free
        notFull.signal();
        return job;
    }
}
