package com.taskforge.repository;

import com.taskforge.domain.JobEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobEventRepository extends JpaRepository<JobEvent, Long> {
    List<JobEvent> findByJobIdOrderByCreatedAtDesc(String jobId);
    List<JobEvent> findByJobIdOrderByCreatedAtAsc(String jobId);
    List<JobEvent> findTop50ByOrderByCreatedAtDesc();
}
