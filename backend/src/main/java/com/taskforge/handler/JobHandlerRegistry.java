package com.taskforge.handler;

import com.taskforge.domain.JobType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class JobHandlerRegistry {
    private final Map<JobType, JobHandler> handlers = new EnumMap<>(JobType.class);

    public JobHandlerRegistry(List<JobHandler> handlerList) {
        for (JobHandler handler : handlerList) {
            handlers.put(handler.supportsType(), handler);
        }
    }

    public JobHandler getHandler(JobType jobType) {
        JobHandler handler = handlers.get(jobType);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for JobType: " + jobType);
        }
        return handler;
    }
}
