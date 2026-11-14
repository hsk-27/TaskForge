package com.taskforge.dto.response;

import java.util.List;

public record JobDagResponse(
        JobResponse currentJob,
        JobResponse parentJob,
        List<JobResponse> downstreamJobs,
        Integer pipelineStep
) {}
