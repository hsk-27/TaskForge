package com.taskforge.metrics;

import com.taskforge.domain.JobStatus;
import com.taskforge.dto.response.EngineMetricsResponse;
import com.taskforge.repository.JobRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetricsService {

    private final JobRepository jobRepository;

    public MetricsService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional(readOnly = true)
    public EngineMetricsResponse getMetrics() {
        long total = jobRepository.count();
        long queued = jobRepository.countByStatus(JobStatus.QUEUED);
        long running = jobRepository.countByStatus(JobStatus.RUNNING);
        long retryWait = jobRepository.countByStatus(JobStatus.RETRY_WAIT);
        long succeeded = jobRepository.countByStatus(JobStatus.SUCCEEDED);
        long failed = jobRepository.countByStatus(JobStatus.FAILED);
        long cancelled = jobRepository.countByStatus(JobStatus.CANCELLED);

        long terminalTotal = succeeded + failed;
        double successRate = terminalTotal > 0 ? ((double) succeeded / terminalTotal) * 100.0 : 0.0;
        double failureRate = terminalTotal > 0 ? ((double) failed / terminalTotal) * 100.0 : 0.0;

        Double avgExec = jobRepository.getAverageExecutionDurationMs();
        double avgExecMs = avgExec != null ? Math.round(avgExec * 10.0) / 10.0 : 0.0;

        Double avgWait = jobRepository.getAverageQueueWaitDurationMs();
        double avgWaitMs = avgWait != null ? Math.round(avgWait * 10.0) / 10.0 : 0.0;

        // Measured real throughput: jobs completed within the trailing 60 seconds
        long recentCompletions = jobRepository.countByCompletedAtAfter(Instant.now().minusSeconds(60));
        double throughput = (double) recentCompletions;

        List<Object[]> rawFailures = jobRepository.getTopFailureReasons(PageRequest.of(0, 5));
        List<Map<String, Object>> topFailures = new ArrayList<>();
        if (rawFailures != null) {
            for (Object[] row : rawFailures) {
                if (row.length >= 2 && row[0] != null) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("reason", row[0].toString());
                    map.put("count", row[1]);
                    topFailures.add(map);
                }
            }
        }

        return new EngineMetricsResponse(
                total,
                queued,
                running,
                retryWait,
                succeeded,
                failed,
                cancelled,
                Math.round(successRate * 10.0) / 10.0,
                Math.round(failureRate * 10.0) / 10.0,
                avgExecMs,
                avgWaitMs,
                Math.round(throughput * 10.0) / 10.0,
                topFailures
        );
    }
}
