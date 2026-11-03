package com.taskforge.event;

import com.taskforge.domain.Job;
import com.taskforge.domain.JobEvent;
import com.taskforge.domain.JobStatus;
import com.taskforge.repository.JobEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class JobEventPublisher {

    private final JobEventRepository eventRepository;
    private final SseEventBroadcaster sseBroadcaster;

    public JobEventPublisher(JobEventRepository eventRepository, SseEventBroadcaster sseBroadcaster) {
        this.eventRepository = eventRepository;
        this.sseBroadcaster = sseBroadcaster;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishEvent(String jobId, String jobName, String eventType, JobStatus status, String message, String workerId) {
        JobEvent event = new JobEvent(jobId, eventType, message, workerId);
        eventRepository.save(event);

        JobLifecycleEvent lifecycleEvent = new JobLifecycleEvent(
                jobId,
                jobName,
                eventType,
                status,
                message,
                workerId,
                Instant.now()
        );
        sseBroadcaster.broadcast(lifecycleEvent);
    }

    public void publishEvent(Job job, String eventType, String message) {
        publishEvent(job.getId(), job.getName(), eventType, job.getStatus(), message, job.getWorkerId());
    }

    public void publishProgress(String jobId, String workerId, int percent, String stage) {
        sseBroadcaster.broadcastProgress(jobId, workerId, percent, stage);
    }
}
