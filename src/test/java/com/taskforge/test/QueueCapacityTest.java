package com.taskforge.test;

import com.taskforge.engine.TaskForgeEngine;
import com.taskforge.model.Job;
import com.taskforge.model.JobResult;
import com.taskforge.model.JobStatus;
import com.taskforge.queue.BoundedJobQueue;
import com.taskforge.queue.QueueFullException;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.taskforge.test.Assert.*;

/**
 * Validates bounded buffer capacity limits, backpressure handling, and non-blocking rejection.
 */
public class QueueCapacityTest {

    public void testCapacityLimitAndOfferRejection() {
        BoundedJobQueue queue = new BoundedJobQueue(2);

        Job j1 = Job.of("j1", "J1", ctx -> JobResult.success(""));
        Job j2 = Job.of("j2", "J2", ctx -> JobResult.success(""));
        Job j3 = Job.of("j3", "J3", ctx -> JobResult.success(""));

        assertTrue(queue.offer(j1), "First job should fit in queue");
        assertTrue(queue.offer(j2), "Second job should fit in queue");
        assertTrue(queue.isFull(), "Queue should report isFull == true");
        assertEquals(0, queue.remainingCapacity(), "Remaining capacity should be 0");

        // Third job must be rejected
        assertFalse(queue.offer(j3), "Third job must be rejected when queue is full");
        assertEquals(2, queue.size(), "Queue size must remain 2");
    }

    public void testEngineSubmissionRejectionWhenFull() {
        // Engine with 1 worker and queue capacity 1
        // We submit a slow job that worker picks up, then 1 job that fills queue, then a 3rd job that gets rejected
        TaskForgeEngine engine = new TaskForgeEngine(1, 1);
        // Do NOT start the engine so no worker dequeues anything
        Job j1 = Job.of("j1", "J1", ctx -> JobResult.success(""));
        Job j2 = Job.of("j2", "J2", ctx -> JobResult.success(""));

        engine.submit(j1);
        assertEquals(JobStatus.QUEUED, j1.getStatus(), "j1 should be QUEUED");

        // j2 should trigger QueueFullException
        QueueFullException ex = assertThrows(QueueFullException.class, () -> engine.submit(j2));
        assertEquals(1, ex.getCapacity(), "Capacity reported in exception should be 1");
        assertEquals(JobStatus.FAILED, j2.getStatus(), "Rejected job must transition to FAILED status");
        assertNotNull(j2.getResult(), "Rejected job must have a failure result");
        assertTrue(j2.getResult().getMessage().contains("capacity"), "Failure message should explain capacity rejection");
    }

    public void testBlockingPutWaitsForConsumer() throws Exception {
        BoundedJobQueue queue = new BoundedJobQueue(1);
        Job j1 = Job.of("j1", "J1", ctx -> JobResult.success(""));
        Job j2 = Job.of("j2", "J2", ctx -> JobResult.success(""));

        queue.put(j1); // Queue is now full (1/1)

        AtomicBoolean putCompleted = new AtomicBoolean(false);
        CountDownLatch threadStarted = new CountDownLatch(1);

        Thread producerThread = new Thread(() -> {
            try {
                threadStarted.countDown();
                // This call should block because queue is full
                queue.put(j2);
                putCompleted.set(true);
            } catch (InterruptedException ignored) {}
        });

        producerThread.start();
        assertTrue(threadStarted.await(1, TimeUnit.SECONDS), "Producer thread should start");

        // Give it a brief moment to block
        Thread.sleep(100);
        assertFalse(putCompleted.get(), "Producer must block while queue is full");

        // Now consume j1; this frees a slot and signals notFull
        Job dequeued = queue.take();
        assertEquals("j1", dequeued.getId(), "Dequeued job must be j1");

        // Wait for producer thread to finish put(j2)
        producerThread.join(1000);
        assertTrue(putCompleted.get(), "Producer must complete put once space is freed");
        assertEquals(1, queue.size(), "Queue size should now be 1");
        assertEquals("j2", queue.take().getId(), "Next dequeued job must be j2");
    }
}
