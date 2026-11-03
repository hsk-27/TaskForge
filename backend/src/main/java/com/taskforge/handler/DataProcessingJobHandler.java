package com.taskforge.handler;

import com.taskforge.domain.Job;
import com.taskforge.domain.JobType;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Simulates processing a batch of structured data records with statistical aggregation.
 */
@Component
public class DataProcessingJobHandler implements JobHandler {

    @Override
    public JobType supportsType() {
        return JobType.DATA_PROCESSING;
    }

    @Override
    public JobExecutionResult execute(Job job, String workerId) throws Exception {
        return execute(job, workerId, null);
    }

    @Override
    public JobExecutionResult execute(Job job, String workerId, JobProgressListener progressListener) throws Exception {
        int records = 2500;
        Random rng = new Random(job.getId().hashCode());

        if (progressListener != null) {
            progressListener.onProgress(10, "Ingesting dataset stream (" + records + " records)");
        }

        double sum = 0.0;
        double sumSq = 0.0;
        int anomalies = 0;
        int step = Math.max(1, records / 5);

        for (int i = 0; i < records; i++) {
            if (i % 250 == 0 && Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Worker thread interrupted during DATA_PROCESSING workload");
            }
            double val = rng.nextGaussian() * 10.0 + 50.0;
            sum += val;
            sumSq += val * val;
            if (val > 80.0 || val < 20.0) {
                anomalies++;
            }
            if (progressListener != null && (i % step == 0 || i == records - 1)) {
                int pct = Math.min(95, 20 + (int) (((long) i * 75) / records));
                progressListener.onProgress(pct, "Aggregating metrics (" + i + "/" + records + ")");
            }
        }

        double mean = sum / records;
        double variance = (sumSq / records) - (mean * mean);

        if (progressListener != null) {
            progressListener.onProgress(100, "Statistical distribution compiled");
        }

        String output = String.format("Processed %d records: mean=%.2f, variance=%.2f, anomalies=%d",
                records, mean, variance, anomalies);

        return JobExecutionResult.success("Data processing completed", output);
    }
}
