package com.taskforge.repository;

import com.taskforge.domain.Job;
import com.taskforge.domain.JobStatus;
import com.taskforge.domain.JobType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, String> {

    @Query("""
        SELECT j FROM Job j
        WHERE (:status IS NULL OR j.status = :status)
          AND (:jobType IS NULL OR j.jobType = :jobType)
          AND (:query IS NULL OR LOWER(j.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(j.id) LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    Page<Job> findFiltered(
            @Param("status") JobStatus status,
            @Param("jobType") JobType jobType,
            @Param("query") String query,
            Pageable pageable
    );

    List<Job> findByStatus(JobStatus status);

    List<Job> findByStatusIn(List<JobStatus> statuses);

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    List<Job> findByStatusOrderByPriorityDescCreatedAtAsc(JobStatus status, Pageable pageable);

    List<Job> findByStatusAndScheduledExecutionAtBefore(JobStatus status, Instant cutoff);

    @Query("SELECT j FROM Job j WHERE j.status = 'RETRY_WAIT' AND (j.scheduledExecutionAt IS NULL OR j.scheduledExecutionAt <= :cutoff)")
    List<Job> findReadyRetries(@Param("cutoff") Instant cutoff);

    List<Job> findByStatusAndStartedAtBefore(JobStatus status, Instant cutoff);

    @Query("SELECT j FROM Job j WHERE j.payload LIKE CONCAT('%', :parentJobId, '%')")
    List<Job> findDependentJobs(@Param("parentJobId") String parentJobId);

    long countByStatus(JobStatus status);

    long countByCompletedAtAfter(Instant cutoff);

    @Query("SELECT AVG(j.executionDurationMs) FROM Job j WHERE j.status = 'SUCCEEDED' AND j.executionDurationMs IS NOT NULL")
    Double getAverageExecutionDurationMs();

    @Query("SELECT AVG(j.queueWaitDurationMs) FROM Job j WHERE j.queueWaitDurationMs IS NOT NULL")
    Double getAverageQueueWaitDurationMs();

    @Query("SELECT j.lastErrorMessage, COUNT(j) FROM Job j WHERE j.status = 'FAILED' AND j.lastErrorMessage IS NOT NULL GROUP BY j.lastErrorMessage ORDER BY COUNT(j) DESC")
    List<Object[]> getTopFailureReasons(Pageable pageable);
}
