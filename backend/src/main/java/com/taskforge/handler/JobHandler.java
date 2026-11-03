package com.taskforge.handler;

import com.taskforge.domain.Job;
import com.taskforge.domain.JobType;

public interface JobHandler {
    JobType supportsType();

    JobExecutionResult execute(Job job, String workerId) throws Exception;

    /**
     * Executes the job with a real-time progress listener.
     * Default implementation delegates to execute(job, workerId).
     */
    default JobExecutionResult execute(Job job, String workerId, JobProgressListener progressListener) throws Exception {
        return execute(job, workerId);
    }
}
