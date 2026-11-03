package com.taskforge.test;

import com.taskforge.model.Job;
import com.taskforge.model.JobResult;
import com.taskforge.queue.BoundedJobQueue;

import java.util.List;

import static com.taskforge.test.Assert.*;

/**
 * Validates strict FIFO queue ordering and circular buffer wrap-around mechanics.
 */
public class QueueOrderTest {

    public void testStrictFifoOrder() throws InterruptedException {
        BoundedJobQueue queue = new BoundedJobQueue(5);

        Job j1 = Job.of("job-1", "Job 1", ctx -> JobResult.success("1"));
        Job j2 = Job.of("job-2", "Job 2", ctx -> JobResult.success("2"));
        Job j3 = Job.of("job-3", "Job 3", ctx -> JobResult.success("3"));

        queue.put(j1);
        queue.put(j2);
        queue.put(j3);

        assertEquals(3, queue.size(), "Queue size should be 3");
        assertEquals("job-1", queue.take().getId(), "First dequeued job must be job-1");
        assertEquals("job-2", queue.take().getId(), "Second dequeued job must be job-2");
        assertEquals("job-3", queue.take().getId(), "Third dequeued job must be job-3");
        assertTrue(queue.isEmpty(), "Queue should now be empty");
    }

    public void testCircularBufferWraparound() throws InterruptedException {
        BoundedJobQueue queue = new BoundedJobQueue(3);

        // Fill and drain multiple times to force circular buffer indices (head & tail) to wrap around
        for (int cycle = 0; cycle < 5; cycle++) {
            Job a = Job.of("c" + cycle + "-a", "A", ctx -> JobResult.success("ok"));
            Job b = Job.of("c" + cycle + "-b", "B", ctx -> JobResult.success("ok"));

            assertTrue(queue.offer(a), "Should enqueue a");
            assertTrue(queue.offer(b), "Should enqueue b");

            assertEquals(a.getId(), queue.poll().getId(), "Must dequeue a in FIFO order");

            Job c = Job.of("c" + cycle + "-c", "C", ctx -> JobResult.success("ok"));
            assertTrue(queue.offer(c), "Should enqueue c");

            assertEquals(b.getId(), queue.poll().getId(), "Must dequeue b in FIFO order");
            assertEquals(c.getId(), queue.poll().getId(), "Must dequeue c in FIFO order");
            assertTrue(queue.isEmpty(), "Queue must be empty after cycle " + cycle);
        }
    }

    public void testToListMaintainsFifoSequence() throws InterruptedException {
        BoundedJobQueue queue = new BoundedJobQueue(4);
        queue.put(Job.of("j1", "J1", ctx -> JobResult.success("")));
        queue.put(Job.of("j2", "J2", ctx -> JobResult.success("")));
        queue.take(); // remove j1, head advances

        queue.put(Job.of("j3", "J3", ctx -> JobResult.success("")));
        queue.put(Job.of("j4", "J4", ctx -> JobResult.success("")));

        List<Job> snapshot = queue.toList();
        assertEquals(3, snapshot.size(), "Snapshot should have 3 jobs");
        assertEquals("j2", snapshot.get(0).getId(), "First in list should be j2");
        assertEquals("j3", snapshot.get(1).getId(), "Second in list should be j3");
        assertEquals("j4", snapshot.get(2).getId(), "Third in list should be j4");
    }
}
