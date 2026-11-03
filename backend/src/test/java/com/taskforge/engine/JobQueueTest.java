package com.taskforge.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class JobQueueTest {

    @Test
    void testBoundedCapacityAndOffer() {
        JobQueue queue = new JobQueue(2);
        assertEquals(2, queue.getCapacity());
        assertEquals(2, queue.remainingCapacity());
        assertTrue(queue.isEmpty());

        assertTrue(queue.offer("job-1"));
        assertTrue(queue.offer("job-2"));
        assertTrue(queue.isFull());
        assertEquals(0, queue.remainingCapacity());

        // Capacity limit: must reject third item
        assertFalse(queue.offer("job-3"));
    }

    @Test
    void testFifoOrdering() throws InterruptedException {
        JobQueue queue = new JobQueue(5);
        queue.offer("first");
        queue.offer("second");
        queue.offer("third");

        assertEquals("first", queue.poll(Duration.ofMillis(10)));
        assertEquals("second", queue.poll());
        assertEquals("third", queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void testRemove() {
        JobQueue queue = new JobQueue(3);
        queue.offer("a");
        queue.offer("b");
        queue.offer("c");

        assertTrue(queue.remove("b"));
        assertEquals(2, queue.size());
        assertEquals("a", queue.poll());
        assertEquals("c", queue.poll());
    }

    @Test
    void testPriorityPreemptionAndTieBreaking() {
        JobQueue queue = new JobQueue(5);
        queue.offer("low-1", 0);
        queue.offer("low-2", 0);
        queue.offer("high-1", 10);
        queue.offer("high-2", 10);
        queue.offer("urgent", 50);

        // Expect: urgent (50) -> high-1 (10) -> high-2 (10) -> low-1 (0) -> low-2 (0)
        assertEquals("urgent", queue.poll());
        assertEquals("high-1", queue.poll());
        assertEquals("high-2", queue.poll());
        assertEquals("low-1", queue.poll());
        assertEquals("low-2", queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void testDeduplication() {
        JobQueue queue = new JobQueue(5);
        assertTrue(queue.offer("job-dup"));
        assertTrue(queue.offer("job-dup")); // Already in buffer, returns true but does not duplicate
        assertEquals(1, queue.size());
        assertEquals("job-dup", queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void testPollItemPreservesPriority() {
        JobQueue queue = new JobQueue(5);
        queue.offer("normal-job", 0);
        queue.offer("vip-job", 100);

        QueuedJobItem first = queue.pollItem();
        assertNotNull(first);
        assertEquals("vip-job", first.getJobId());
        assertEquals(100, first.getPriority());

        QueuedJobItem second = queue.pollItem();
        assertNotNull(second);
        assertEquals("normal-job", second.getJobId());
        assertEquals(0, second.getPriority());
    }
}
