package com.taskforge.service;

import com.taskforge.domain.Job;
import com.taskforge.domain.JobStatus;
import com.taskforge.domain.JobType;
import com.taskforge.dto.request.BatchJobRequest;
import com.taskforge.dto.request.CreateJobRequest;
import com.taskforge.dto.response.BatchJobResponse;
import com.taskforge.dto.response.JobAttemptResponse;
import com.taskforge.dto.response.JobEventResponse;
import com.taskforge.dto.response.JobResponse;
import com.taskforge.engine.ExecutionEngine;
import com.taskforge.event.JobEventPublisher;
import com.taskforge.exception.InvalidStateTransitionException;
import com.taskforge.exception.JobNotFoundException;
import com.taskforge.exception.QueueFullException;
import com.taskforge.repository.JobAttemptRepository;
import com.taskforge.repository.JobEventRepository;
import com.taskforge.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final JobAttemptRepository attemptRepository;
    private final JobEventRepository eventRepository;
    private final ExecutionEngine executionEngine;
    private final JobEventPublisher eventPublisher;

    public JobService(
            JobRepository jobRepository,
            JobAttemptRepository attemptRepository,
            JobEventRepository eventRepository,
            ExecutionEngine executionEngine,
            JobEventPublisher eventPublisher
    ) {
        this.jobRepository = jobRepository;
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
        this.executionEngine = executionEngine;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public JobResponse createJob(CreateJobRequest request) {
        Job job = new Job(
                request.name(),
                request.jobType(),
                request.resolvedPriority(),
                request.payload(),
                request.resolvedMaxAttempts()
        );

        boolean isDelayed = request.resolvedDelaySeconds() > 0;
        if (isDelayed) {
            job.setStatus(JobStatus.RETRY_WAIT);
            job.setScheduledExecutionAt(Instant.now().plusSeconds(request.resolvedDelaySeconds()));
        }

        job = jobRepository.save(job);

        if (isDelayed) {
            eventPublisher.publishEvent(job, "JOB_SCHEDULED",
                    String.format("Job scheduled for delayed execution in %ds", request.resolvedDelaySeconds()));
        } else {
            eventPublisher.publishEvent(job, "JOB_SUBMITTED", "Job created and submitted to queue");

            final String jobId = job.getId();
            final int priority = job.getPriority();
            // DBMS & Concurrency: Register a Post-Commit Hook.
            // Why: If we enqueue into the in-memory buffer before the database transaction commits,
            // an active worker thread might poll the queue and attempt to read the job from the DB
            // before the INSERT statement has committed, causing a race condition (JobNotFoundException).
            // afterCommit guarantees the database record is fully committed before workers can claim it!
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            executionEngine.enqueue(jobId, priority);
                        } catch (QueueFullException qfe) {
                            log.warn("Job {} persisted but rejected from immediate queue buffer (queue full)", jobId);
                        }
                    }
                });
            } else {
                try {
                    executionEngine.enqueue(jobId, priority);
                } catch (QueueFullException qfe) {
                    log.warn("Job {} persisted but rejected from immediate queue buffer (queue full)", jobId);
                }
            }
        }

        return JobResponse.fromEntity(job);
    }

    @Transactional
    public BatchJobResponse createBatch(BatchJobRequest request) {
        int count = request.count();
        List<String> ids = new ArrayList<>(count);
        List<String> toEnqueue = new ArrayList<>(count);
        final int batchPriority = request.priority() != null ? request.priority() : 0;
        int rejected = 0;

        JobType[] sampleTypes = JobType.values();
        String previousJobId = null;

        for (int i = 1; i <= count; i++) {
            JobType type = request.jobType() != null ? request.jobType() : sampleTypes[i % sampleTypes.length];
            String name = request.resolvedIsSequential()
                    ? String.format("%s-step%02d [%s]", request.resolvedPrefix(), i, type)
                    : String.format("%s-%03d [%s]", request.resolvedPrefix(), i, type);

            String payload;
            if (request.resolvedIsSequential() && previousJobId != null) {
                payload = String.format("{\"pipelineStep\":%d,\"dependsOnJobId\":\"%s\",\"durationMs\":%d,\"failureRate\":%.2f}",
                        i, previousJobId, request.resolvedDurationMs(), request.resolvedFailureRate());
            } else {
                payload = (request.payload() != null && !request.payload().isBlank())
                        ? request.payload()
                        : String.format("{\"batchIndex\":%d,\"durationMs\":%d,\"failureRate\":%.2f}",
                                i, request.resolvedDurationMs(), request.resolvedFailureRate());
            }

            Job job = new Job(
                    name,
                    type,
                    batchPriority,
                    payload,
                    request.resolvedMaxAttempts()
            );
            job = jobRepository.save(job);
            ids.add(job.getId());

            // For sequential pipelines, only enqueue step 1 initially;
            // subsequent steps trigger automatically as prerequisites complete.
            if (!request.resolvedIsSequential() || i == 1) {
                toEnqueue.add(job.getId());
            }

            previousJobId = job.getId();
            eventPublisher.publishEvent(job, "JOB_SUBMITTED",
                    request.resolvedIsSequential()
                            ? "Workflow pipeline step #" + i + (i > 1 ? " queued awaiting parent step" : " queued ready for execution")
                            : "Batch job item #" + i + " submitted");
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String id : toEnqueue) {
                        try {
                            executionEngine.enqueue(id, batchPriority);
                        } catch (QueueFullException qfe) {
                            // Queue capacity reached; periodic drain will feed it
                        }
                    }
                }
            });
        } else {
            for (String id : toEnqueue) {
                try {
                    executionEngine.enqueue(id, batchPriority);
                } catch (QueueFullException qfe) {
                    rejected++;
                }
            }
        }

        String msg = request.resolvedIsSequential()
                ? String.format("Submitted sequential workflow pipeline with %d steps", count)
                : String.format("Submitted %d jobs to persistent storage", count);
        return new BatchJobResponse(count, rejected, ids, msg);
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(String id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        return JobResponse.fromEntity(job);
    }

    @Transactional(readOnly = true)
    public Page<JobResponse> listJobs(JobStatus status, JobType jobType, String query, Pageable pageable) {
        return jobRepository.findFiltered(status, jobType, query, pageable)
                .map(JobResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<JobAttemptResponse> getAttempts(String jobId) {
        return attemptRepository.findByJobIdOrderByAttemptNumberAsc(jobId).stream()
                .map(JobAttemptResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobEventResponse> getEvents(String jobId) {
        return eventRepository.findByJobIdOrderByCreatedAtDesc(jobId).stream()
                .map(JobEventResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobEventResponse> getRecentEvents() {
        return eventRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(JobEventResponse::fromEntity)
                .toList();
    }

    @Transactional
    public JobResponse cancelJob(String id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        if (job.getStatus().isTerminal()) {
            throw new InvalidStateTransitionException(
                    "Cannot cancel job " + id + " because it is already terminal: " + job.getStatus());
        }

        job.markCancelled();
        jobRepository.save(job);
        executionEngine.getQueue().remove(id);
        boolean interrupted = executionEngine.interruptJobIfRunning(id);

        eventPublisher.publishEvent(job, "JOB_CANCELLED",
                interrupted ? "Job cancelled by operator request; active worker thread interrupted"
                            : "Job cancelled by operator request");
        return JobResponse.fromEntity(job);
    }

    @Transactional
    public JobResponse retryJob(String id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        if (job.getStatus() != JobStatus.FAILED && job.getStatus() != JobStatus.CANCELLED) {
            throw new InvalidStateTransitionException(
                    "Only FAILED or CANCELLED jobs can be manually retried. Current status: " + job.getStatus());
        }

        job.resetForManualRetry();
        jobRepository.save(job);

        eventPublisher.publishEvent(job, "JOB_MANUAL_RETRY", "Manual retry initiated by operator");

        final String jobId = job.getId();
        final int priority = job.getPriority();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        executionEngine.enqueue(jobId, priority);
                    } catch (QueueFullException qfe) {
                        log.warn("Job {} reset for retry but queue buffer full; drain will pick it up", jobId);
                    }
                }
            });
        } else {
            try {
                executionEngine.enqueue(jobId, priority);
            } catch (QueueFullException qfe) {
                log.warn("Job {} reset for retry but queue buffer full; drain will pick it up", jobId);
            }
        }

        return JobResponse.fromEntity(job);
    }

    @Transactional(readOnly = true)
    public Page<JobResponse> getDlq(Pageable pageable) {
        return jobRepository.findByStatus(JobStatus.FAILED, pageable).map(JobResponse::fromEntity);
    }

    @Transactional
    public int replayDlq() {
        List<Job> failedJobs = jobRepository.findByStatus(JobStatus.FAILED);
        if (failedJobs.isEmpty()) {
            return 0;
        }

        List<Job> toReplay = new ArrayList<>(failedJobs);
        for (Job job : toReplay) {
            job.resetForManualRetry();
            jobRepository.save(job);
            eventPublisher.publishEvent(job, "JOB_REPLAYED_DLQ", "Job replayed from Dead Letter Queue");
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Job job : toReplay) {
                        try {
                            executionEngine.enqueue(job.getId(), job.getPriority());
                        } catch (QueueFullException ignored) {}
                    }
                }
            });
        } else {
            for (Job job : toReplay) {
                try {
                    executionEngine.enqueue(job.getId(), job.getPriority());
                } catch (QueueFullException ignored) {}
            }
        }
        return toReplay.size();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportDiagnostics() {
        long totalMem = Runtime.getRuntime().totalMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        long maxMem = Runtime.getRuntime().maxMemory();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("exportedAt", Instant.now().toString());
        report.put("system", Map.of(
                "availableProcessors", Runtime.getRuntime().availableProcessors(),
                "jvmUsedMemoryMb", (totalMem - freeMem) / (1024 * 1024),
                "jvmMaxMemoryMb", maxMem / (1024 * 1024),
                "ecoMode", executionEngine.isEcoMode()
        ));
        report.put("engine", Map.of(
                "isRunning", executionEngine.isRunning(),
                "isPaused", executionEngine.isPaused(),
                "queueSize", executionEngine.getQueue().size(),
                "queueCapacity", executionEngine.getQueue().getCapacity(),
                "activeWorkers", executionEngine.getActiveWorkerCount()
        ));
        report.put("statusCounts", Map.of(
                "total", jobRepository.count(),
                "queued", jobRepository.countByStatus(JobStatus.QUEUED),
                "running", jobRepository.countByStatus(JobStatus.RUNNING),
                "retryWait", jobRepository.countByStatus(JobStatus.RETRY_WAIT),
                "succeeded", jobRepository.countByStatus(JobStatus.SUCCEEDED),
                "failed", jobRepository.countByStatus(JobStatus.FAILED),
                "cancelled", jobRepository.countByStatus(JobStatus.CANCELLED)
        ));

        List<Job> allJobs = jobRepository.findAll();
        List<Map<String, Object>> exportedJobs = new ArrayList<>();
        for (Job job : allJobs) {
            Map<String, Object> jobData = new LinkedHashMap<>();
            jobData.put("job", JobResponse.fromEntity(job));
            jobData.put("attempts", attemptRepository.findByJobIdOrderByAttemptNumberAsc(job.getId()));
            jobData.put("events", eventRepository.findByJobIdOrderByCreatedAtAsc(job.getId()));
            exportedJobs.add(jobData);
        }
        report.put("jobs", exportedJobs);

        return report;
    }

    @Transactional
    public int cancelAllPending() {
        List<Job> pending = jobRepository.findByStatusIn(List.of(JobStatus.QUEUED, JobStatus.RETRY_WAIT));
        for (Job job : pending) {
            job.markCancelled();
            jobRepository.save(job);
            executionEngine.getQueue().remove(job.getId());
            eventPublisher.publishEvent(job, "JOB_CANCELLED", "Cancelled via operator bulk cancellation");
        }
        return pending.size();
    }

    @Transactional
    public int purgeTerminalJobs() {
        List<Job> terminal = jobRepository.findByStatusIn(List.of(JobStatus.SUCCEEDED, JobStatus.FAILED, JobStatus.CANCELLED));
        if (terminal.isEmpty()) return 0;
        jobRepository.deleteAllInBatch(terminal);
        return terminal.size();
    }

    @Transactional
    public void clearAllData() {
        log.warn("Clearing all demo data from database and in-memory queue");
        executionEngine.getQueue().clear();
        attemptRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
    }
}
