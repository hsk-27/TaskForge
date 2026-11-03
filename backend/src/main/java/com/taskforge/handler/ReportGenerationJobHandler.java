package com.taskforge.handler;

import com.taskforge.domain.Job;
import com.taskforge.domain.JobType;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Generates structured analytical summaries and reports from synthetic operational data.
 */
@Component
public class ReportGenerationJobHandler implements JobHandler {

    @Override
    public JobType supportsType() {
        return JobType.REPORT_GENERATION;
    }

    @Override
    public JobExecutionResult execute(Job job, String workerId) throws Exception {
        return execute(job, workerId, null);
    }

    @Override
    public JobExecutionResult execute(Job job, String workerId, JobProgressListener progressListener) throws Exception {
        if (progressListener != null) {
            progressListener.onProgress(20, "Querying telemetry history & aggregates");
        }
        Thread.sleep(40);

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Worker thread interrupted during REPORT_GENERATION");
        }

        if (progressListener != null) {
            progressListener.onProgress(60, "Formatting markdown diagnostics tables");
        }
        Thread.sleep(40);

        if (progressListener != null) {
            progressListener.onProgress(100, "Report synthesis finalized");
        }

        String report = String.format("""
            # TaskForge Operational Report
            - Generated At: %s
            - Worker: %s
            - Job Reference: %s
            - Status: Verified Clean
            - System Health: 99.98%% Uptime
            """, Instant.now(), workerId, job.getId());

        return JobExecutionResult.success("Report generated successfully", report.strip());
    }
}
