package com.taskforge.repository;

import com.taskforge.domain.JobAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobAttemptRepository extends JpaRepository<JobAttempt, String> {
    List<JobAttempt> findByJobIdOrderByAttemptNumberAsc(String jobId);
}
