package com.taskforge.demo;

import com.taskforge.engine.TaskForgeConfig;
import com.taskforge.engine.TaskForgeEngine;
import com.taskforge.model.Job;
import com.taskforge.model.JobStatus;
import com.taskforge.model.SimulatedJob;
import com.taskforge.queue.QueueFullException;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Command-line demonstration of TaskForge Milestone 1.
 *
 * Demonstrates:
 * - Engine startup with 3 worker threads and a bounded queue of capacity 6
 * - Real-time execution event streaming
 * - Batch submission of varied deterministic workloads (fast success, slow success, failures, exceptions)
 * - Graceful shutdown and queue draining
 * - Post-execution tabular summary of job metrics and final outcomes
 */
public class TaskForgeCliDemo {

    public static void main(String[] args) throws InterruptedException {
        printHeader();

        int workerCount = 3;
        int queueCapacity = 6;
        TaskForgeConfig config = TaskForgeConfig.of(workerCount, queueCapacity);

        System.out.println("Configuring TaskForge Engine:");
        System.out.println("  * Worker Threads : " + workerCount);
        System.out.println("  * Queue Capacity : " + queueCapacity + " (Bounded FIFO buffer)");
        System.out.println("--------------------------------------------------------------------------------");

        try (TaskForgeEngine engine = new TaskForgeEngine(config).start()) {

            // Register real-time event listener to print event stream to standard out
            engine.addEventListener(event -> {
                System.out.println(event.formatLog());
            });

            System.out.println("Engine started. Submitting a batch of 8 simulated jobs...\n");
            System.out.println("LIVE EVENT STREAM:");
            System.out.println("================================================================================");

            List<Job> batch = List.of(
                    SimulatedJob.success("job-101", "Fast Data Transform", Duration.ofMillis(80), "Transformed 1,000 records"),
                    SimulatedJob.success("job-102", "External API Fetch", Duration.ofMillis(160), "HTTP 200 OK (payload 4.2KB)"),
                    SimulatedJob.failure("job-103", "Database Write", Duration.ofMillis(100), "Connection pool timeout after 100ms"),
                    SimulatedJob.exceptional("job-104", "JSON Parsing", Duration.ofMillis(50), new IllegalArgumentException("Unexpected token '0x00'")),
                    SimulatedJob.success("job-105", "Thumbnail Generation", Duration.ofMillis(120), "Rendered 256x256 image"),
                    SimulatedJob.success("job-106", "Email Notification", Duration.ofMillis(90), "Dispatched to user@example.com"),
                    SimulatedJob.success("job-107", "Audit Log Flush", Duration.ofMillis(40), "Flushed 50 audit entries to disk"),
                    SimulatedJob.success("job-108", "Cache Eviction", Duration.ofMillis(30), "Purged 12 expired keys")
            );

            // Submit each job to the engine
            for (Job job : batch) {
                try {
                    engine.submit(job);
                } catch (QueueFullException qfe) {
                    System.err.println("[REJECTED BY CAPACITY LIMIT] " + qfe.getMessage());
                }
                // Brief pause to simulate rapid burst submission
                Thread.sleep(25);
            }

            System.out.println("================================================================================");
            System.out.println("Batch submission complete. Initiating graceful shutdown and queue draining...\n");

            engine.shutdown();
            boolean cleanlyTerminated = engine.awaitTermination(Duration.ofSeconds(10));

            if (cleanlyTerminated) {
                System.out.println("\nAll worker threads terminated cleanly.");
            } else {
                System.err.println("\nWarning: Worker pool timed out awaiting termination.");
            }

            // Print summary table of final results
            printSummaryTable(engine.getAllJobs());
        }

        printFooter();
    }

    private static void printHeader() {
        System.out.println("================================================================================");
        System.out.println("              TASKFORGE - JOB EXECUTION ENGINE (MILESTONE 1)                    ");
        System.out.println("         Single-Process Concurrency & Bounded Buffer Systems Lab                ");
        System.out.println("================================================================================");
    }

    private static void printSummaryTable(List<Job> jobs) {
        System.out.println("\n================================================================================");
        System.out.println("                          FINAL EXECUTION SUMMARY                               ");
        System.out.println("================================================================================");
        System.out.printf("%-9s | %-22s | %-18s | %-9s | %-6s | %-6s | %s%n",
                "Job ID", "Job Name", "Assigned Worker", "Status", "Wait", "Exec", "Outcome Details");
        System.out.println("--------------------------------------------------------------------------------");

        List<Job> sorted = jobs.stream()
                .sorted(Comparator.comparing(Job::getId))
                .toList();

        for (Job job : sorted) {
            String status = job.getStatus() == JobStatus.COMPLETED ? "COMPLETED" : "FAILED";
            String worker = job.getAssignedWorker() != null ? job.getAssignedWorker() : "N/A";
            long waitMs = job.getQueueWaitDuration().toMillis();
            long execMs = job.getExecutionDuration().toMillis();
            String details = job.getResult() != null ? job.getResult().getMessage() : "No result";

            System.out.printf("%-9s | %-22s | %-18s | %-9s | %4dms | %4dms | %s%n",
                    job.getId(),
                    truncate(job.getName(), 22),
                    worker,
                    status,
                    waitMs,
                    execMs,
                    truncate(details, 35));
        }

        System.out.println("--------------------------------------------------------------------------------");
        long completedCount = jobs.stream().filter(j -> j.getStatus() == JobStatus.COMPLETED).count();
        long failedCount = jobs.stream().filter(j -> j.getStatus() == JobStatus.FAILED).count();
        System.out.printf("Total Jobs: %d | Completed: %d | Failed: %d%n",
                jobs.size(), completedCount, failedCount);
    }

    private static void printFooter() {
        System.out.println("\nMilestone 1 Key Systems Engineering Takeaways:");
        System.out.println("  1. Bounded Buffer: Backpressure prevents memory exhaustion under high load.");
        System.out.println("  2. Concurrency Safety: ReentrantLock & Condition primitives coordinate workers safely.");
        System.out.println("  3. Graceful Draining: Engine shutdown prevents new submissions while draining the queue.");
        System.out.println("  4. Deterministic Simulation: Failures and exceptions are safely caught at the boundary.");
        System.out.println("================================================================================");
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
