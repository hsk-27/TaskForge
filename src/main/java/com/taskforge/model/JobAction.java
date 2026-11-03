package com.taskforge.model;

/**
 * Functional interface representing the executable workload of a Job.
 */
@FunctionalInterface
public interface JobAction {
    /**
     * Executes the unit of work.
     *
     * @param context the runtime context of this execution attempt
     * @return the JobResult indicating success or failure
     * @throws Exception if an unhandled error occurs during execution
     */
    JobResult execute(JobExecutionContext context) throws Exception;
}
